cmake_minimum_required(VERSION 3.22.1)
project(tempestsdr)

# ── libusb-1.0 (bundled minimal Android port) ──────────────────────────────
add_library(libusb STATIC
    libusb/core.c
    libusb/descriptor.c
    libusb/hotplug.c
    libusb/io.c
    libusb/sync.c
    libusb/strerror.c
    libusb/os/linux_usbfs.c
    libusb/os/linux_netlink.c
    libusb/os/events_posix.c
    libusb/os/threads_posix.c
)
target_include_directories(libusb PUBLIC libusb libusb/os)
target_compile_definitions(libusb PRIVATE
    LIBUSB_DESCRIBE=""
    ENABLE_LOGGING=1
    _GNU_SOURCE
    OS_LINUX
    THREADS_POSIX
    PLATFORM_POSIX
    USE_SYSTEM_LOGGING_FACILITY
    HAVE_CLOCK_GETTIME=1
)
target_link_libraries(libusb log)

# ── librtlsdr (minimal subset needed for IQ streaming) ─────────────────────
add_library(rtlsdr STATIC
    rtlsdr/rtl-sdr.c
    rtlsdr/tuner_e4k.c
    rtlsdr/tuner_fc0012.c
    rtlsdr/tuner_fc0013.c
    rtlsdr/tuner_fc2580.c
    rtlsdr/tuner_r82xx.c
)
target_include_directories(rtlsdr PUBLIC rtlsdr/include rtlsdr libusb)
target_link_libraries(rtlsdr libusb)

# ── TempestSDR core DSP ─────────────────────────────────────────────────────
add_library(tempestsdr_core STATIC
    tsdr/TSDRLibrary.c
    tsdr/dsp.c
    tsdr/syncdetector.c
    tsdr/superbandwidth.c
    tsdr/circbuff.c
    tsdr/threading.c
)
target_include_directories(tempestsdr_core PUBLIC tsdr/include)
target_link_libraries(tempestsdr_core android log m)

# ── JNI bridge (.so loaded by Java) ────────────────────────────────────────
add_library(tempestsdr_jni SHARED
    jni_bridge.c
    rtlsdr_plugin.c
)
target_include_directories(tempestsdr_jni PRIVATE
    tsdr/include
    rtlsdr/include
    rtlsdr
    libusb
)
target_link_libraries(tempestsdr_jni
    tempestsdr_core
    rtlsdr
    libusb
    android
    log
    m
)
