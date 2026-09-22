/*
 * jni_bridge.c
 *
 * JNI glue between com.tempestsdr.core.TSDRLibrary (Java) and the
 * TempestSDR C engine + RTL-SDR plugin.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <android/bitmap.h>

#include "tsdr/include/TSDRLibrary.h"
#include "rtlsdr_plugin.h"

#define TAG  "TempestJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ── Global state ────────────────────────────────────────────────────── */
static JavaVM     *g_jvm    = NULL;
static jweak       g_obj    = NULL;
static jmethodID   g_onFrame  = NULL;
static jmethodID   g_onError  = NULL;

/* ── JNI_OnLoad ──────────────────────────────────────────────────────── */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void)reserved;
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

/* ── Frame callback: runs on DSP thread, calls back into Java ────────── */
static void native_frame_cb(float *pixels, int width, int height, void *ctx)
{
    (void)ctx;
    if (!g_jvm || !g_obj) return;

    JNIEnv *env;
    int attached = 0;
    int rc = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);
    if (rc == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != 0) return;
        attached = 1;
    }

    jobject obj = (*env)->NewLocalRef(env, g_obj);
    if (obj) {
        int total = width * height;
        jfloatArray jarr = (*env)->NewFloatArray(env, total);
        if (jarr) {
            (*env)->SetFloatArrayRegion(env, jarr, 0, total, pixels);
            (*env)->CallVoidMethod(env, obj, g_onFrame, jarr, width, height);
            (*env)->DeleteLocalRef(env, jarr);
        }
        (*env)->DeleteLocalRef(env, obj);
    }

    if (attached) (*g_jvm)->DetachCurrentThread(g_jvm);
}

/* ── Error callback ──────────────────────────────────────────────────── */
static void native_error_cb(int code, const char *msg, void *ctx)
{
    (void)ctx;
    LOGE("TSDRError code=%d: %s", code, msg);
    if (!g_jvm || !g_obj) return;

    JNIEnv *env;
    int attached = 0;
    if ((*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != 0) return;
        attached = 1;
    }

    jobject obj  = (*env)->NewLocalRef(env, g_obj);
    if (obj) {
        jstring jmsg = (*env)->NewStringUTF(env, msg ? msg : "unknown error");
        (*env)->CallVoidMethod(env, obj, g_onError, code, jmsg);
        (*env)->DeleteLocalRef(env, jmsg);
        (*env)->DeleteLocalRef(env, obj);
    }

    if (attached) (*g_jvm)->DetachCurrentThread(g_jvm);
}

/* ═══════════════════════════════════════════════════════════════════════
 * Java_com_radar_blewifi_drone_core_TSDRLibrary_*
 * ═══════════════════════════════════════════════════════════════════════ */

JNIEXPORT jlong JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeCreate(JNIEnv *env, jobject thiz)
{
    tsdr_lib_t *lib = tsdr_create();
    jclass cls = (*env)->GetObjectClass(env, thiz);
    g_onFrame = (*env)->GetMethodID(env, cls, "onFrame", "([FII)V");
    g_onError = (*env)->GetMethodID(env, cls, "onError", "(ILjava/lang/String;)V");
    g_obj = (*env)->NewWeakGlobalRef(env, thiz);
    LOGI("nativeCreate -> %p", (void*)lib);
    return (jlong)(uintptr_t)lib;
}

JNIEXPORT void JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeDestroy(JNIEnv *env, jobject thiz, jlong ptr)
{
    (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    tsdr_destroy(lib);
    if (g_obj) { (*env)->DeleteWeakGlobalRef(env, g_obj); g_obj = NULL; }
}

JNIEXPORT jint JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeConnectFd(JNIEnv *env, jobject thiz,
                                                      jlong ptr, jint fd)
{
    (void)env; (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    return rtlsdr_plugin_open(lib, fd);
}

JNIEXPORT jint JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeDisconnect(JNIEnv *env, jobject thiz, jlong ptr)
{
    (void)env; (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    return rtlsdr_plugin_close(lib);
}

JNIEXPORT jint JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeSetFreq(JNIEnv *env, jobject thiz,
                                                    jlong ptr, jlong freq)
{
    (void)env; (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    return rtlsdr_plugin_set_freq(lib, (uint64_t)freq);
}

JNIEXPORT jint JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeClearBuffers(JNIEnv *env, jobject thiz, jlong ptr)
{
    (void)env; (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    return tsdr_clear_buffers(lib);
}

JNIEXPORT jint JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeSetGain(JNIEnv *env, jobject thiz,
                                                    jlong ptr, jdouble gain)
{
    (void)env; (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    return rtlsdr_plugin_set_gain(lib, gain);
}

JNIEXPORT jint JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeSetSampleRate(JNIEnv *env, jobject thiz,
                                                          jlong ptr, jint rate)
{
    (void)env; (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    rtlsdr_plugin_set_rate(lib, (uint32_t)rate);
    return tsdr_set_samplerate(lib, (uint32_t)rate);
}

JNIEXPORT jint JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeSetResolution(JNIEnv *env, jobject thiz,
                                                          jlong ptr,
                                                          jint height, jdouble refresh)
{
    (void)env; (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    return tsdr_set_resolution(lib, height, refresh);
}

JNIEXPORT jint JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeStart(JNIEnv *env, jobject thiz, jlong ptr)
{
    (void)env; (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    int rc = tsdr_start(lib, native_frame_cb, native_error_cb, NULL);
    if (rc == TSDR_OK)
        rc = rtlsdr_plugin_start_async(lib);
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeStop(JNIEnv *env, jobject thiz, jlong ptr)
{
    (void)env; (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    rtlsdr_plugin_stop_async(lib);
    return tsdr_stop(lib);
}

JNIEXPORT jboolean JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeIsRunning(JNIEnv *env, jobject thiz, jlong ptr)
{
    (void)env; (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    return (jboolean)tsdr_is_running(lib);
}

JNIEXPORT jint JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeSetParam(JNIEnv *env, jobject thiz,
                                                     jlong ptr, jint id, jdouble val)
{
    (void)env; (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    return tsdr_set_param(lib, id, val);
}

JNIEXPORT jint JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeSetGainOffset(JNIEnv *env, jobject thiz,
                                                          jlong ptr, jdouble offset)
{
    (void)env; (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    return tsdr_set_gain_offset(lib, offset);
}

JNIEXPORT jint JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeSetShift(JNIEnv *env, jobject thiz,
                                                     jlong ptr, jdouble shift)
{
    (void)env; (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    return tsdr_set_shift(lib, shift);
}

JNIEXPORT jfloatArray JNICALL
Java_com_radar_blewifi_drone_core_TSDRLibrary_nativeGetAutocorr(JNIEnv *env, jobject thiz, jlong ptr)
{
    (void)thiz;
    tsdr_lib_t *lib = (tsdr_lib_t *)(uintptr_t)ptr;
    float  buf[2048];
    int    len = 2048;
    tsdr_get_autocorr(lib, buf, &len);
    jfloatArray arr = (*env)->NewFloatArray(env, len);
    if (arr) {
        (*env)->SetFloatArrayRegion(env, arr, 0, len, buf);
    }
    return arr;
}

