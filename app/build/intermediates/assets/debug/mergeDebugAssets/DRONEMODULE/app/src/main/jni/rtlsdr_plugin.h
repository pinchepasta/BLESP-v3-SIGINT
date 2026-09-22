#pragma once
#include <stdint.h>
#include "tsdr/include/TSDRLibrary.h"

/* Called from JNI with the file descriptor obtained from Android USB Host API */
int  rtlsdr_plugin_open  (tsdr_lib_t *lib, int usb_fd);
int  rtlsdr_plugin_close (tsdr_lib_t *lib);
int  rtlsdr_plugin_set_freq    (tsdr_lib_t *lib, uint64_t freq_hz);
int  rtlsdr_plugin_set_gain    (tsdr_lib_t *lib, double gain_db);
int  rtlsdr_plugin_set_rate    (tsdr_lib_t *lib, uint32_t rate);
int  rtlsdr_plugin_start_async (tsdr_lib_t *lib);
int  rtlsdr_plugin_stop_async  (tsdr_lib_t *lib);
