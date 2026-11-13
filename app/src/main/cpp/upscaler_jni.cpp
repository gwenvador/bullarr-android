// Pont JNI : upscaling d'une page (Bitmap ARGB_8888) via ncnn Vulkan, avec Real-ESRGAN ou Real-CUGAN.
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include "realesrgan.h"
#include "realcugan.h"

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "BullarrUpscale", __VA_ARGS__)

// Vérifié entre deux tuiles par les deux moteurs.
std::atomic<bool> realesrgan_cancel(false);
static RealESRGAN* g_esrgan = nullptr;
static RealCUGAN* g_cugan = nullptr;
static int g_scale = 0;
static std::mutex g_lock;
static bool g_gpu = false;

static std::string str(JNIEnv* env, jstring s) {
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

static void release() {
    delete g_esrgan;
    g_esrgan = nullptr;
    delete g_cugan;
    g_cugan = nullptr;
    g_scale = 0;
}

/** engine : 0 = Real-ESRGAN, 1 = Real-CUGAN (noise : -1 conservateur, 0 sans débruitage, 1 à 3 débruitage). */
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_gwenvador_bullarr_data_Upscaler_nativeInit(JNIEnv* env, jclass, jstring param, jstring bin,
                                                          jint engine, jint scale, jint noise, jboolean heavy) {
    std::lock_guard<std::mutex> guard(g_lock);
    // Changement de modèle : l'ancien est libéré, l'instance Vulkan reste.
    release();
    if (!g_gpu) {
        ncnn::create_gpu_instance();
        if (ncnn::get_gpu_count() == 0) {
            ncnn::destroy_gpu_instance();
            return env->NewStringUTF("aucun processeur graphique Vulkan");
        }
        g_gpu = true;
    }
    uint32_t heap = ncnn::get_gpu_device(0)->get_heap_budget();
    // Petites tuiles : chaque envoi au GPU reste court, l'affichage de l'app garde la main entre deux tuiles
    // (et l'arrêt est pris en compte vite). Les modèles lourds ont des tuiles encore plus petites.
    const int tile = heavy ? (heap > 550 ? 64 : 32) : (heap > 550 ? 128 : 64);
    if (engine == 1) {
        auto* m = new RealCUGAN(0, false);
        if (m->load(str(env, param), str(env, bin)) != 0) { delete m; return env->NewStringUTF("modèle illisible"); }
        m->scale = scale;
        m->noise = noise;
        m->syncgap = 0;
        m->prepadding = scale == 2 ? 18 : scale == 3 ? 14 : 19;
        m->tilesize = tile;
        g_cugan = m;
    } else {
        auto* m = new RealESRGAN(0, false);
        if (m->load(str(env, param), str(env, bin)) != 0) { delete m; return env->NewStringUTF("modèle illisible"); }
        m->scale = scale;
        m->prepadding = 10;
        m->tilesize = tile;
        g_esrgan = m;
    }
    g_scale = scale;
    LOG("GPU %s, heap %u Mo, moteur %d, ×%d, bruit %d, tuiles %d", ncnn::get_gpu_info(0).device_name(), heap, engine, scale, noise, tile);
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_gwenvador_bullarr_data_Upscaler_nativeCancel(JNIEnv*, jclass, jboolean on) {
    realesrgan_cancel.store(on);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_gwenvador_bullarr_data_Upscaler_nativeUpscale(JNIEnv* env, jclass, jobject src, jobject dst) {
    std::lock_guard<std::mutex> guard(g_lock);
    if (!g_esrgan && !g_cugan) return -1;
    AndroidBitmapInfo si, di;
    if (AndroidBitmap_getInfo(env, src, &si) < 0 || AndroidBitmap_getInfo(env, dst, &di) < 0) return -2;
    if (si.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || di.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return -3;
    const int k = g_scale;
    if ((int)di.width != (int)si.width * k || (int)di.height != (int)si.height * k) return -4;
    const int w = si.width, h = si.height;

    std::vector<unsigned char> rgb((size_t)w * h * 3);
    void* sp = nullptr;
    if (AndroidBitmap_lockPixels(env, src, &sp) < 0) return -5;
    for (int y = 0; y < h; y++) {
        const unsigned char* row = (const unsigned char*)sp + (size_t)y * si.stride;
        unsigned char* o = rgb.data() + (size_t)y * w * 3;
        for (int x = 0; x < w; x++) { o[0] = row[0]; o[1] = row[1]; o[2] = row[2]; o += 3; row += 4; }
    }
    AndroidBitmap_unlockPixels(env, src);

    ncnn::Mat in(w, h, (void*)rgb.data(), (size_t)3, 3);
    ncnn::Mat out(w * k, h * k, (size_t)3, 3);
    const int rc = g_cugan ? g_cugan->process(in, out) : g_esrgan->process(in, out);
    if (rc != 0) return -6;

    void* dp = nullptr;
    if (AndroidBitmap_lockPixels(env, dst, &dp) < 0) return -7;
    const unsigned char* res = (const unsigned char*)out.data;
    for (int y = 0; y < h * k; y++) {
        unsigned char* row = (unsigned char*)dp + (size_t)y * di.stride;
        const unsigned char* i = res + (size_t)y * w * k * 3;
        for (int x = 0; x < w * k; x++) { row[0] = i[0]; row[1] = i[1]; row[2] = i[2]; row[3] = 255; row += 4; i += 3; }
    }
    AndroidBitmap_unlockPixels(env, dst);
    return 0;
}
