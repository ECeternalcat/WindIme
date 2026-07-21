#include <jni.h>
#include <android/log.h>
#include <vector>
#include <string>

#define LOG_TAG "GarahoRimeNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Phase-1 stub implementation of the JNI surface declared in
// com.garaho.ime.rime.RimeBridge (design doc §3.3.1).
//
// librime 1.x is wired in here in Phase 4:
//   rime::Api* api = rime::api();
//   api->initialize(...);
//   api->process_key(...);
//   ...
// Until then every call is logged and returns an empty/false result so the
// Java side stays null-safe via RimeBridge.isLoaded().

namespace {

std::vector<std::string> g_last_candidates;

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_garaho_ime_rime_RimeBridge_rimeInit(JNIEnv* env, jclass, jstring shared_dir, jstring user_dir) {
    const char* s = env->GetStringUTFChars(shared_dir, nullptr);
    const char* u = env->GetStringUTFChars(user_dir, nullptr);
    LOGI("rimeInit stub (shared=%s, user=%s)", s ? s : "<null>", u ? u : "<null>");
    if (s) env->ReleaseStringUTFChars(shared_dir, s);
    if (u) env->ReleaseStringUTFChars(user_dir, u);
    g_last_candidates.clear();
}

JNIEXPORT jboolean JNICALL
Java_com_garaho_ime_rime_RimeBridge_rimeProcessKey(JNIEnv*, jclass, jint keycode, jint mask) {
    LOGI("rimeProcessKey stub (key=%d, mask=%d)", (int)keycode, (int)mask);
    g_last_candidates.clear();
    return JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_garaho_ime_rime_RimeBridge_rimeGetCandidates(JNIEnv* env, jclass) {
    jclass str_class = env->FindClass("java/lang/String");
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(g_last_candidates.size()), str_class, nullptr);
    for (size_t i = 0; i < g_last_candidates.size(); ++i) {
        jstring s = env->NewStringUTF(g_last_candidates[i].c_str());
        env->SetObjectArrayElement(out, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);
    }
    return out;
}

JNIEXPORT void JNICALL
Java_com_garaho_ime_rime_RimeBridge_rimeCommit(JNIEnv*, jclass) {
    LOGI("rimeCommit stub");
    g_last_candidates.clear();
}

}  // extern "C"
