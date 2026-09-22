/*
 * rtlsdr_plugin.c
 *
 * Wraps librtlsdr for Android.
 */

#include "rtlsdr_plugin.h"
#include "tsdr/include/TSDRLibrary.h"
#include "rtl-sdr.h"

#include <stdlib.h>
#include <pthread.h>
#include <android/log.h>

#define TAG "RTLSDRPlugin"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/*
 * Use standard librtlsdr buffer settings for better compatibility.
 * 32 buffers of 16KB each = 512KB total.
 */
#define IQ_BUF_NUM         32
#define IQ_BUF_LEN         16384

typedef struct {
    rtlsdr_dev_t *dev;
    tsdr_lib_t   *tsdr;
} plugin_ctx_t;

static plugin_ctx_t g_ctx = {NULL, NULL};
static pthread_t    g_reader_thread;
static volatile int g_stop = 0;
static volatile int g_thread_running = 0;

static void rtlsdr_cb(unsigned char *buf, uint32_t len, void *userctx)
{
    plugin_ctx_t *ctx = (plugin_ctx_t *)userctx;
    if (g_stop) return;

    if (ctx->tsdr) {
        tsdr_push_samples(ctx->tsdr, buf, len);
    }
}

static void *reader_thread(void *arg)
{
    plugin_ctx_t *ctx = (plugin_ctx_t *)arg;
    LOGI("Reader thread starting...");
    g_thread_running = 1;

    /* This will block until rtlsdr_cancel_async is called */
    int rc = rtlsdr_read_async(ctx->dev, rtlsdr_cb, ctx, IQ_BUF_NUM, IQ_BUF_LEN);

    if (rc != 0 && !g_stop) {
        LOGE("rtlsdr_read_async error: %d", rc);
    }

    g_thread_running = 0;
    LOGI("Reader thread exiting");
    return NULL;
}

int rtlsdr_plugin_open(tsdr_lib_t *lib, int usb_fd)
{
    LOGI("rtlsdr_plugin_open(fd=%d)", usb_fd);
    if (usb_fd <= 0) return TSDR_ERR_PLUGIN;

    if (g_ctx.dev) {
        rtlsdr_close(g_ctx.dev);
        g_ctx.dev = NULL;
    }

    int rc = rtlsdr_open_fd(&g_ctx.dev, usb_fd);
    if (rc != 0) {
        LOGE("rtlsdr_open_fd failed: %d", rc);
        return rc;
    }

    g_ctx.tsdr = lib;
    tsdr_set_dev(lib, g_ctx.dev);

    rtlsdr_reset_buffer(g_ctx.dev);
    rtlsdr_set_tuner_gain_mode(g_ctx.dev, 1);
    rtlsdr_set_agc_mode(g_ctx.dev, 0);

    LOGI("RTL-SDR opened successfully");
    return TSDR_OK;
}

int rtlsdr_plugin_close(tsdr_lib_t *lib)
{
    rtlsdr_plugin_stop_async(lib);
    if (g_ctx.dev) {
        rtlsdr_close(g_ctx.dev);
        g_ctx.dev = NULL;
        tsdr_set_dev(lib, NULL);
    }
    return TSDR_OK;
}

int rtlsdr_plugin_set_freq(tsdr_lib_t *lib, uint64_t freq_hz)
{
    if (!g_ctx.dev) return TSDR_ERR_PLUGIN;
    rtlsdr_set_center_freq(g_ctx.dev, (uint32_t)freq_hz);
    return TSDR_OK;
}

int rtlsdr_plugin_set_gain(tsdr_lib_t *lib, double gain_db)
{
    if (!g_ctx.dev) return TSDR_ERR_PLUGIN;
    rtlsdr_set_tuner_gain(g_ctx.dev, (int)(gain_db * 10.0));
    return TSDR_OK;
}

int rtlsdr_plugin_set_rate(tsdr_lib_t *lib, uint32_t rate)
{
    if (!g_ctx.dev) return TSDR_ERR_PLUGIN;
    rtlsdr_set_sample_rate(g_ctx.dev, rate);
    return TSDR_OK;
}

int rtlsdr_plugin_start_async(tsdr_lib_t *lib)
{
    if (!g_ctx.dev) return TSDR_ERR_PLUGIN;
    if (g_thread_running) return TSDR_OK;

    g_stop = 0;
    if (pthread_create(&g_reader_thread, NULL, reader_thread, &g_ctx) != 0) {
        LOGE("Failed to create reader thread");
        return TSDR_ERR_PLUGIN;
    }
    return TSDR_OK;
}

int rtlsdr_plugin_stop_async(tsdr_lib_t *lib)
{
    if (!g_thread_running) return TSDR_OK;
    g_stop = 1;
    if (g_ctx.dev) rtlsdr_cancel_async(g_ctx.dev);
    pthread_join(g_reader_thread, NULL);
    g_stop = 0;
    return TSDR_OK;
}
