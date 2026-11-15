#include <jni.h>

extern "C" int accel_inject_c(const char *target, const char *lib);

extern "C" JNIEXPORT jint JNICALL
Java_com_accel_loader_AccelInject_nativeInject(JNIEnv *env, jclass, jstring jTarget, jstring jLib) {
    if (!jTarget || !jLib) return -11;
    const char *target = env->GetStringUTFChars(jTarget, nullptr);
    const char *lib = env->GetStringUTFChars(jLib, nullptr);
    if (!target || !lib) {
        if (target) env->ReleaseStringUTFChars(jTarget, target);
        if (lib) env->ReleaseStringUTFChars(jLib, lib);
        return -11;
    }
    const int rc = accel_inject_c(target, lib);
    env->ReleaseStringUTFChars(jTarget, target);
    env->ReleaseStringUTFChars(jLib, lib);
    return rc;
}