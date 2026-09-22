/*
 * TSDRLibrary.c  –  Android port of martinmarinov/TempestSDR
 */

#include "include/TSDRLibrary.h"
#include "circbuff.h"
#include "syncdetector.h"
#include "dsp.h"

#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <pthread.h>
#include <time.h>
#include <android/log.h>

#define TAG "TSDRCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define CIRCBUFF_FACTOR   8
#define DEFAULT_RATE      2500000u
#define DEFAULT_HEIGHT    628
#define DEFAULT_REFRESH   60.0
#define IQ_BYTES_PER_SAMP 2
#define NORMALISE_ALPHA   0.05f

struct tsdr_lib {
    void            *dev;
    circbuff_t       iq_buf;
    float           *frame_buf;
    int              frame_width;
    int              frame_height;
    double           refresh_hz;
    uint32_t         sample_rate;
    syncdetector_t   sync;
    dsp_state_t      dsp;
    double           gain_offset;
    double           shift;
    int              params[8];
    tsdr_frame_cb_t  frame_cb;
    tsdr_error_cb_t  error_cb;
    void            *cb_ctx;
    pthread_t        dsp_thread;
    volatile int     running;
    pthread_mutex_t  lock;
};

static uint64_t get_now_ms() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static void *dsp_loop(void *arg)
{
    tsdr_lib_t *lib = (tsdr_lib_t *)arg;
    LOGI("DSP thread starting");

    int blk = 0;
    int height = 0;
    uint8_t *iq_raw = NULL;
    float *mag_buf = NULL;
    int row = 0;
    uint64_t frame_cnt = 0;
    uint64_t last_log_time = get_now_ms();

    while (lib->running) {
        pthread_mutex_lock(&lib->lock);
        if (blk != lib->frame_width || height != lib->frame_height) {
            blk = lib->frame_width;
            height = lib->frame_height;
            iq_raw = realloc(iq_raw, blk * IQ_BYTES_PER_SAMP);
            mag_buf = realloc(mag_buf, blk * sizeof(float));

            /* Safely reallocate frame buffer and sync detector inside the lock */
            int hw = blk * height;
            free(lib->frame_buf);
            lib->frame_buf = calloc(hw, sizeof(float));
            syncdetector_free(&lib->sync);
            syncdetector_init(&lib->sync, blk);

            row = 0;
            LOGI("DSP resolution updated: %dx%d", blk, height);
        }
        pthread_mutex_unlock(&lib->lock);

        if (!iq_raw || !mag_buf || blk <= 0) {
            struct timespec ts = {0, 5000000}; // 5ms
            nanosleep(&ts, NULL);
            continue;
        }

            /* Pull one line worth of data - this will block if no data */
            if (circbuff_pop(&lib->iq_buf, iq_raw, blk * IQ_BYTES_PER_SAMP) < 0) {
                /* If it timed out or was cleared, just retry the loop */
                continue;
            }

        /* Convert IQ -> Magnitude (Grain) */
        for (int i = 0; i < blk; i++) {
            float I = ((float)iq_raw[2*i]   - 127.5f) / 127.5f;
            float Q = ((float)iq_raw[2*i+1] - 127.5f) / 127.5f;
            mag_buf[i] = sqrtf(I*I + Q*Q);
        }

        if (lib->gain_offset != 0.0) {
            float g = (float)(1.0 + lib->gain_offset);
            for (int i = 0; i < blk; i++) mag_buf[i] *= g;
        }

        if (lib->params[6] /* PARAM_LOW_PASS_BEFORE_SYNC */)
            dsp_lowpass(&lib->dsp, mag_buf, blk);

        pthread_mutex_lock(&lib->lock);

        if (lib->params[2] /* PARAM_AUTOCORR_PLOTS_RESET */) {
            syncdetector_reset(&lib->sync);
            lib->params[2] = 0;
        }

        syncdetector_push(&lib->sync, mag_buf, blk);

        /* Fill frame buffer line by line */
        const int hw = blk * height;
        const int shift_val = (int)lib->shift;
        for (int i = 0; i < blk; i++) {
            int x = (i + shift_val) % blk;
            if (x < 0) x += blk;
            int px = row * blk + x;
            if (px >= 0 && px < hw) {
                if (lib->params[7] /* PARAM_AUTOGAIN_AFTER_PROCESSING */)
                    lib->frame_buf[px] = lib->frame_buf[px] * (1.0f - NORMALISE_ALPHA) + mag_buf[i] * NORMALISE_ALPHA;
                else
                    lib->frame_buf[px] = mag_buf[i];
            }
        }

        row++;
        int frame_ready = 0;
        if (row >= height) {
            row = 0;
            frame_ready = 1;
            frame_cnt++;
        }

        tsdr_frame_cb_t cb = lib->frame_cb;
        void *cb_ctx = lib->cb_ctx;
        float *fbuf = lib->frame_buf;
        int cur_blk = blk;
        int cur_height = height;

        pthread_mutex_unlock(&lib->lock);

        if (frame_ready && cb && fbuf) {
            cb(fbuf, cur_blk, cur_height, cb_ctx);

            uint64_t now = get_now_ms();
            if (now - last_log_time > 2000) {
                LOGI("DSP heart-beat: %llu frames total", (unsigned long long)frame_cnt);
                last_log_time = now;
            }
        }
    }

    free(iq_raw);
    free(mag_buf);
    LOGI("DSP thread exiting");
    return NULL;
}

void tsdr_set_dev(tsdr_lib_t *lib, void *dev) {
    if (lib) lib->dev = dev;
}

tsdr_lib_t *tsdr_create(void)
{
    tsdr_lib_t *lib = calloc(1, sizeof(tsdr_lib_t));
    if (!lib) return NULL;
    pthread_mutex_init(&lib->lock, NULL);
    lib->sample_rate  = DEFAULT_RATE;
    lib->frame_height = DEFAULT_HEIGHT;
    lib->refresh_hz   = DEFAULT_REFRESH;
    lib->frame_width  = (int)((double)DEFAULT_RATE / (DEFAULT_HEIGHT * DEFAULT_REFRESH));
    size_t buf_sz = (size_t)lib->frame_height * lib->frame_width * IQ_BYTES_PER_SAMP * CIRCBUFF_FACTOR;
    if (buf_sz < 2*1024*1024) buf_sz = 2*1024*1024;
    circbuff_init(&lib->iq_buf, buf_sz);
    int hw = lib->frame_height * lib->frame_width;
    lib->frame_buf = calloc(hw, sizeof(float));
    syncdetector_init(&lib->sync, lib->frame_width);
    dsp_init(&lib->dsp);
    LOGI("tsdr_create OK");
    return lib;
}

void tsdr_destroy(tsdr_lib_t *lib)
{
    if (!lib) return;
    tsdr_stop(lib);
    circbuff_free(&lib->iq_buf);
    free(lib->frame_buf);
    syncdetector_free(&lib->sync);
    pthread_mutex_destroy(&lib->lock);
    free(lib);
}

int tsdr_set_resolution(tsdr_lib_t *lib, int height, double refresh_hz)
{
    if (!lib || height <= 0 || refresh_hz <= 0) return TSDR_ERR_INVALID_PARAM;
    pthread_mutex_lock(&lib->lock);
    lib->frame_height = height;
    lib->refresh_hz   = refresh_hz;
    lib->frame_width  = (int)((double)lib->sample_rate / (height * refresh_hz));

    /* If not running, reallocate immediately. If running, the DSP loop will notice
       the dimension change and reallocate safely. */
    if (!lib->running) {
        int hw = lib->frame_height * lib->frame_width;
        free(lib->frame_buf);
        lib->frame_buf = calloc(hw, sizeof(float));
        syncdetector_free(&lib->sync);
        syncdetector_init(&lib->sync, lib->frame_width);
    }
    pthread_mutex_unlock(&lib->lock);
    return TSDR_OK;
}

int tsdr_set_center_freq(tsdr_lib_t *lib, uint64_t freq_hz)
{
    /* Handled by plugin */
    return TSDR_OK;
}

int tsdr_clear_buffers(tsdr_lib_t *lib)
{
    if (!lib) return -1;
    circbuff_clear(&lib->iq_buf);
    pthread_mutex_lock(&lib->lock);
    syncdetector_reset(&lib->sync);
    /* Set flag to reset EMA in sync detector on next line */
    lib->params[2] = 1;
    pthread_mutex_unlock(&lib->lock);
    return TSDR_OK;
}

int tsdr_set_samplerate(tsdr_lib_t *lib, uint32_t rate)
{
    if (!lib || rate == 0) return TSDR_ERR_INVALID_PARAM;
    pthread_mutex_lock(&lib->lock);
    lib->sample_rate = rate;
    pthread_mutex_unlock(&lib->lock);
    return tsdr_set_resolution(lib, lib->frame_height, lib->refresh_hz);
}

int tsdr_set_param(tsdr_lib_t *lib, int id, double value)
{
    if (!lib || id < 0 || id >= 8) return TSDR_ERR_INVALID_PARAM;
    lib->params[id] = (int)value;
    return TSDR_OK;
}

int tsdr_set_gain_offset(tsdr_lib_t *lib, double offset)
{
    if (lib) lib->gain_offset = offset;
    return TSDR_OK;
}

int tsdr_set_shift(tsdr_lib_t *lib, double shift)
{
    if (lib) lib->shift = shift;
    return TSDR_OK;
}

int tsdr_start(tsdr_lib_t *lib, tsdr_frame_cb_t frame_cb, tsdr_error_cb_t error_cb, void *ctx)
{
    if (!lib || lib->running) return -1;
    lib->frame_cb = frame_cb;
    lib->error_cb = error_cb;
    lib->cb_ctx   = ctx;
    lib->running  = 1;
    pthread_create(&lib->dsp_thread, NULL, dsp_loop, lib);
    return 0;
}

int tsdr_stop(tsdr_lib_t *lib)
{
    if (!lib || !lib->running) return -1;
    lib->running = 0;
    pthread_join(lib->dsp_thread, NULL);
    return 0;
}

int tsdr_is_running(tsdr_lib_t *lib) { return lib ? lib->running : 0; }

int tsdr_get_autocorr(tsdr_lib_t *lib, float *out, int *len)
{
    if (!lib) return -1;
    pthread_mutex_lock(&lib->lock);
    int rc = syncdetector_get_autocorr(&lib->sync, out, len);
    pthread_mutex_unlock(&lib->lock);
    return rc;
}

void tsdr_push_samples(tsdr_lib_t *lib, const uint8_t *buf, size_t len)
{
    if (lib && lib->running) circbuff_push(&lib->iq_buf, buf, len);
}
