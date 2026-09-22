#pragma once
/*
 * TSDRLibrary.h  –  Android port of martinmarinov/TempestSDR
 * Core DSP API (mirrors the original TSDRLibrary.h interface).
 */

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ── Error / return codes ─────────────────────────────────────────────── */
#define TSDR_OK                    0
#define TSDR_ERR_MEMORY           -1
#define TSDR_ERR_PLUGIN           -2
#define TSDR_ERR_SAMPLE_RATE      -3
#define TSDR_ERR_ALREADY_RUNNING  -4
#define TSDR_ERR_NOT_RUNNING      -5
#define TSDR_ERR_INVALID_PARAM    -6

/* ── Tunable parameters (mirrors original PARAM enum) ────────────────── */
#define PARAM_AUTOSHIFT                  0
#define PARAM_PLLFRAMERATE               1
#define PARAM_AUTOCORR_PLOTS_RESET       2
#define PARAM_AUTOCORR_PLOTS_OFF         3
#define PARAM_SUPERRESOLUTION            4
#define PARAM_NEAREST_NEIGHBOUR_RESAMP   5
#define PARAM_LOW_PASS_BEFORE_SYNC       6
#define PARAM_AUTOGAIN_AFTER_PROCESSING  7

/* ── Types ───────────────────────────────────────────────────────────── */
typedef struct tsdr_lib tsdr_lib_t;

/**
 * Callback invoked for every reconstructed video frame.
 *
 * @param pixels   Greyscale pixel buffer, row-major, size = width*height
 * @param width    Frame width  (may differ from display resolution)
 * @param height   Frame height
 * @param ctx      User context pointer
 */
typedef void (*tsdr_frame_cb_t)(float *pixels, int width, int height, void *ctx);

/**
 * Callback invoked when a processing error occurs.
 * @param code     One of TSDR_ERR_* values
 * @param msg      Human-readable description
 * @param ctx      User context pointer
 */
typedef void (*tsdr_error_cb_t)(int code, const char *msg, void *ctx);

/* ── Lifecycle ───────────────────────────────────────────────────────── */
tsdr_lib_t *tsdr_create(void);
void        tsdr_destroy(tsdr_lib_t *lib);

/* ── RTL-SDR connection (Android: fd from UsbDeviceConnection) ───────── */
void tsdr_set_dev(tsdr_lib_t *lib, void *dev);
int tsdr_connect_rtlsdr_fd(tsdr_lib_t *lib, int usb_fd);
int tsdr_disconnect(tsdr_lib_t *lib);

/* ── SDR parameters ──────────────────────────────────────────────────── */
int tsdr_set_center_freq(tsdr_lib_t *lib, uint64_t freq_hz);
int tsdr_clear_buffers(tsdr_lib_t *lib);
int tsdr_set_gain(tsdr_lib_t *lib, double gain_db);
int tsdr_set_samplerate(tsdr_lib_t *lib, uint32_t rate);

/* ── Video parameters ────────────────────────────────────────────────── */
/**
 * @param height      Pixel rows  (e.g. 628 for 800×600 @ 60 Hz)
 * @param refresh_hz  Refresh rate (e.g. 60.0)
 */
int tsdr_set_resolution(tsdr_lib_t *lib, int height, double refresh_hz);

/* ── Processing control ──────────────────────────────────────────────── */
int tsdr_start(tsdr_lib_t *lib,
               tsdr_frame_cb_t frame_cb,
               tsdr_error_cb_t error_cb,
               void *ctx);
int tsdr_stop(tsdr_lib_t *lib);
int tsdr_is_running(tsdr_lib_t *lib);

/* ── DSP parameters ──────────────────────────────────────────────────── */
int tsdr_set_param(tsdr_lib_t *lib, int param_id, double value);
int tsdr_set_gain_offset(tsdr_lib_t *lib, double offset);
int tsdr_set_shift(tsdr_lib_t *lib, double shift);

/* ── Auto-detection ──────────────────────────────────────────────────── */
/**
 * Returns autocorrelation buffer for framerate estimation.
 * @param out   Caller-allocated float array of length *len
 * @param len   In: capacity; Out: filled length
 */
int tsdr_get_autocorr(tsdr_lib_t *lib, float *out, int *len);

/* Called by rtlsdr_plugin when new samples arrive from USB */
void tsdr_push_samples(tsdr_lib_t *lib, const uint8_t *buf, size_t len);

#ifdef __cplusplus
}
#endif
