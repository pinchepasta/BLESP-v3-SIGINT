package com.radar.blewifi

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.radar.blewifi.databinding.ActivityArchiveBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ArchiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArchiveBinding
    private lateinit var adapter: GroupedArchiveAdapter
    private lateinit var scanner: ScannerManager
    private var allDevices: List<ScanDevice> = emptyList()
    private var isHighContrast = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setupImmersiveMode()

        binding = ActivityArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isHighContrast = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("high_contrast", false)
        scanner = ScannerManager(this)

        setupUI()
        loadData()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
        }
    }

    private fun setupImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    private fun setupUI() {
        if (isHighContrast) {
            binding.root.setBackgroundColor(Color.WHITE)
            binding.headerArchive.setBackgroundColor(Color.parseColor("#F0F0F0"))
            binding.etSearch.setBackgroundResource(R.drawable.status_box_bg_white)
            binding.etSearch.setTextColor(Color.BLACK)
            binding.etSearch.setHintTextColor(Color.GRAY)
            binding.tvEmpty.setTextColor(Color.GRAY)
            binding.btnBack.setColorFilter(Color.WHITE)
        } else {
            binding.btnBack.setColorFilter(Color.parseColor("#FF00FF"))
        }

        binding.btnExport.setColorFilter(if (isHighContrast) Color.WHITE else Color.parseColor("#00FF41"))
        binding.btnExport.setOnClickListener { exportArchive() }

        binding.btnClear.setColorFilter(if (isHighContrast) Color.WHITE else Color.parseColor("#FF0000"))
        binding.btnClear.setOnClickListener { showClearConfirmation() }

        binding.btnBack.setOnClickListener { finish() }

        adapter = GroupedArchiveAdapter { device ->
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra(DetailActivity.EXTRA_DEVICE, device)
            startActivity(intent)
        }
        adapter.isHighContrastMode = isHighContrast
        binding.rvArchive.layoutManager = LinearLayoutManager(this)
        binding.rvArchive.adapter = adapter

        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            filterDevices(text.toString())
        }
    }

    private fun showClearConfirmation() {
        val title = android.text.SpannableString("CLEAR ARCHIVE").apply {
            setSpan(android.text.style.ForegroundColorSpan(if (isHighContrast) Color.BLACK else Color.RED), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setMessage("Are you sure you want to delete all stored signal data?")
            .setPositiveButton("DELETE") { _, _ ->
                getSharedPreferences("scan_archive", MODE_PRIVATE).edit().clear().apply()
                allDevices = emptyList()
                adapter.submitData(emptyMap())
                updateEmptyState()
                Toast.makeText(this, "Archive Cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCEL", null)
            .show()

        dialog.setOnDismissListener { setupImmersiveMode() }

        val bg = android.graphics.drawable.GradientDrawable()
        if (isHighContrast) {
            bg.setColor(Color.WHITE)
            bg.setStroke(4, Color.BLACK)
        } else {
            bg.setColor(Color.parseColor("#99220000")) // Translucent dark red
            bg.setStroke(2, Color.RED)
        }
        bg.cornerRadius = 30f
        dialog.window?.setBackgroundDrawable(bg)

        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.apply {
            setTextColor(if (isHighContrast) Color.BLACK else Color.WHITE)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(if (isHighContrast) Color.BLACK else Color.RED)
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(if (isHighContrast) Color.BLACK else Color.GRAY)
    }

    private fun loadData() {
        allDevices = scanner.getArchivedDevices()
        val grouped = DeviceType.entries.associate { type ->
            val label = typeToLabel(type)
            label to allDevices.filter { it.type == type }
        }
        adapter.submitData(grouped)
        updateEmptyState()
    }

    private fun typeToLabel(type: DeviceType): String = when(type) {
        DeviceType.BLE -> "BLE"
        DeviceType.WIFI -> "WiFi"
        DeviceType.AIRCRAFT -> "Aircraft"
        DeviceType.CAR -> "Car"
        DeviceType.DRONE -> "Drone"
        DeviceType.ESCOOTER -> "Escooter"
        DeviceType.TV -> "Smart TV"
        DeviceType.COMPUTER -> "Computer"
        DeviceType.SMARTPHONE -> "Smartphone"
        DeviceType.PAGER -> "Pager"
        DeviceType.LTE, DeviceType.FIVE_G -> "Cell"
    }

    private fun filterDevices(query: String) {
        val filtered = if (query.isEmpty()) {
            allDevices
        } else {
            val q = query.lowercase()
            allDevices.filter { 
                it.address.lowercase().contains(q) || 
                it.name.lowercase().contains(q) || 
                it.ssid.lowercase().contains(q) ||
                it.typeLabel.lowercase().contains(q) ||
                it.capabilities.lowercase().contains(q)
            }
        }
        
        val grouped = DeviceType.entries.associate { type ->
            val label = typeToLabel(type)
            label to filtered.filter { it.type == type }
        }
        adapter.submitData(grouped)
        updateEmptyState()
    }

    private fun exportArchive() {
        if (allDevices.isEmpty()) {
            Toast.makeText(this, "Archive is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val filename = "BLESP_Archive_${sdf.format(Date())}.txt"
        val report = StringBuilder()
        
        report.append("=== BLESP SIGNAL ARCHIVE ===\n")
        report.append("Generated: ${Date()}\n")
        report.append("Total Entries: ${allDevices.size}\n")
        report.append("============================\n\n")

        allDevices.forEach { d ->
            report.append("DEVICE:   ${d.displayName}\n")
            report.append("TYPE:     ${d.typeLabel}\n")
            report.append("ADDR:     ${d.address}\n")
            report.append("RSSI:     ${d.rssi} dBm\n")
            report.append("DIST:     ${d.distanceLabel}\n")
            
            when (d.type) {
                DeviceType.WIFI -> {
                    report.append("SSID:     ${d.ssid}\n")
                    report.append("CAPS:     ${d.capabilities}\n")
                    report.append("CHAN:     ${d.channel} (${d.frequency}MHz)\n")
                }
                DeviceType.BLE -> {
                    report.append("MFR:      ${d.manufacturer}\n")
                    report.append("UUIDS:    ${d.uuids}\n")
                    if (d.isVulnerableWhisperPair) report.append("VULN:     WhisperPair (CVE-2024-XXXXX)\n")
                    if (d.isAirTag) report.append("INFO:     Apple AirTag / Find My\n")
                }
                DeviceType.AIRCRAFT -> {
                    report.append("SQUAWK:   ${d.squawk ?: "N/A"}\n")
                    report.append("ALT:      ${d.altitude ?: 0} ft\n")
                    report.append("SPEED:    ${d.speed ?: 0} kt\n")
                    report.append("POS:      ${d.lat ?: 0.0}, ${d.lon ?: 0.0}\n")
                    report.append("DEST:     ${d.destination ?: "Unknown"}\n")
                }
                DeviceType.LTE, DeviceType.FIVE_G -> {
                    report.append("OPER:     ${d.mcc}-${d.mnc}\n")
                    report.append("CID/PCI:  ${d.cid} / ${d.pci}\n")
                    report.append("BAND:     ${d.band ?: "N/A"}\n")
                }
                else -> {}
            }
            
            report.append("SEEN:     ${d.seenCount} times\n")
            report.append("LAST:     ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(d.lastSeen))}\n")
            report.append("----------------------------\n")
        }

        try {
            val cacheFile = File(cacheDir, filename)
            FileOutputStream(cacheFile).use { it.write(report.toString().toByteArray()) }

            val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", cacheFile)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "BLESP Signal Archive Export")
                putExtra(Intent.EXTRA_TEXT, "Attached is the exported signal archive from BLESP v3.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, "Export Archive Via..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEmptyState() {
        binding.tvEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }
}
