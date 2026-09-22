package com.radar.blewifi.drone.rtlsdr;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Manages RTL-SDR device enumeration, USB permission requests, and
 * returns a file descriptor suitable for rtlsdr_open_fd() in native code.
 */
public class RtlSdrManager {

    private static final String TAG    = "RtlSdrManager";
    private static final String ACTION = "com.radar.blewifi.drone.USB_PERMISSION";

    /** Known RTL-SDR USB VID/PID pairs */
    private static final int[][] RTL_IDS = {
        {0x0BDA, 0x2832}, // RTL2832U generic
        {0x0BDA, 0x2838}, // RTL2838 (RTL-SDR Blog V3/V4)
        {0x0BDA, 0x2837}, // RTL2837
        {0x0BDA, 0x2840}, // RTL2840
        {0x185B, 0x0620}, // Compro Videomate U620F
        {0x185B, 0x0650}, // Compro Videomate U650F
        {0x185B, 0x0680}, // Compro Videomate U680F
        {0x1F4D, 0x3000}, // G-Tek Electronics Group
        {0x1F4D, 0xA803}, // Zaapa ZT-MINDVBZP
        {0x1B80, 0xD393}, // GTek T803
        {0x1B80, 0xD395}, // Dexatek DK DVB-T Dongle
        {0x1B80, 0xD39D}, // Dexatek DK mini DVB-T Dongle
        {0x0458, 0x707F}, // Genius TVGo DVB-T03 USB dongle
        {0x1F4D, 0xB803}, // NOXON DAB/DAB+ USB dongle
        {0x0EB0, 0x3026}, // NOXON DAB/DAB+ USB dongle
        {0x1D19, 0x1101}, // Dikom USB-DVBT HD
        {0x03F0, 0x6B6D}, // DigitalNow Quad DVB-T
        {0x1B80, 0xD3A4}, // Twintech UT-40
    };

    public interface Callback {
        void onDeviceFound(UsbDevice device);
        void onPermissionGranted(UsbDevice device, int usbFd);
        void onPermissionDenied(UsbDevice device);
        void onNoDevice();
    }

    private final Context    ctx;
    private final UsbManager usbManager;
    private Callback         callback;
    private UsbDevice        pendingDevice;
    private UsbDeviceConnection openConnection;

    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION.equals(intent.getAction())) return;
            UsbDevice device = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
            } else {
                device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            }
            if (device == null) device = pendingDevice;

            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted && device != null) {
                Log.i(TAG, "Permission granted for " + device.getDeviceName());
                openAndCallback(device);
            } else {
                Log.w(TAG, "Permission denied or device null: granted=" + granted + " device=" + (device != null));
                if (callback != null) callback.onPermissionDenied(device);
            }
        }
    };

    public RtlSdrManager(Context ctx) {
        this.ctx        = ctx.getApplicationContext();
        this.usbManager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
    }

    public void registerReceiver() {
        IntentFilter filter = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use RECEIVER_EXPORTED because the broadcast is sent by the system USB service
            ctx.registerReceiver(permissionReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            ctx.registerReceiver(permissionReceiver, filter);
        }
    }

    public void unregisterReceiver() {
        try { ctx.unregisterReceiver(permissionReceiver); } catch (Exception ignored) {}
    }

    /** Find all attached RTL-SDR devices */
    public List<UsbDevice> findDevices() {
        List<UsbDevice> found = new ArrayList<>();
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            if (isRtlSdr(device)) {
                found.add(device);
                Log.i(TAG, "Found RTL-SDR: " + device.getDeviceName()
                      + " VID=" + String.format("%04X", device.getVendorId())
                      + " PID=" + String.format("%04X", device.getProductId()));
            }
        }
        return found;
    }

    /** Request permission for the first found device, or call onNoDevice */
    public void requestFirstDevice(Callback cb) {
        this.callback = cb;
        List<UsbDevice> devices = findDevices();
        if (devices.isEmpty()) {
            if (cb != null) cb.onNoDevice();
            return;
        }
        requestPermission(devices.get(0), cb);
    }

    public void requestPermission(UsbDevice device, Callback cb) {
        this.callback      = cb;
        this.pendingDevice = device;

        if (cb != null) cb.onDeviceFound(device);

        if (usbManager.hasPermission(device)) {
            openAndCallback(device);
            return;
        }

        // Use a unique request code to avoid collision
        int requestCode = device.getDeviceName().hashCode();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode,
                new Intent(ACTION).setPackage(ctx.getPackageName()), flags);
        
        Log.i(TAG, "Requesting USB permission for: " + device.getDeviceName());
        usbManager.requestPermission(device, pi);
    }

    /** Open the device and return the fd through the callback */
    private void openAndCallback(UsbDevice device) {
        try {
            openConnection = usbManager.openDevice(device);
            if (openConnection == null) {
                Log.e(TAG, "Failed to open device: UsbManager.openDevice returned null. " +
                           "Check if another app is using it or if the device was disconnected.");
                if (callback != null) callback.onPermissionDenied(device);
                return;
            }
            int fd = openConnection.getFileDescriptor();
            Log.i(TAG, "Opened device fd=" + fd);
            if (callback != null) callback.onPermissionGranted(device, fd);
        } catch (Exception e) {
            Log.e(TAG, "Exception opening device", e);
            if (callback != null) callback.onPermissionDenied(device);
        }
    }

    public void closeConnection() {
        if (openConnection != null) {
            openConnection.close();
            openConnection = null;
        }
    }

    private boolean isRtlSdr(UsbDevice dev) {
        int vid = dev.getVendorId();
        int pid = dev.getProductId();
        for (int[] id : RTL_IDS) {
            if (id[0] == vid && id[1] == pid) return true;
        }
        return false;
    }
}
