#include <jni.h>
#include <android/log.h>

extern "C"
{
#include "libavcodec/jni.h"
#include "libavformat/avformat.h"
#include <jni.h>

static void android_log_callback(void *ptr, int level, const char *fmt, va_list vl) {
  if (level > av_log_get_level())
    return;

  va_list vl2;
  char line[1024];
  static int print_prefix = 1;

  va_copy(vl2, vl);
  av_log_format_line(ptr, level, fmt, vl2, line, sizeof(line), &print_prefix);
  va_end(vl2);

  __android_log_print(ANDROID_LOG_INFO, "FFMPEG", "%s", line);
}

JNIEXPORT jint JNI_OnLoad(
    JavaVM *vm,
    void *res
) {
  av_log_set_callback(android_log_callback);
  av_jni_set_java_vm(vm, nullptr);
  return JNI_VERSION_1_4;
}

jobject av_dict_to_java(JNIEnv *env, AVDictionary *d) {
  AVDictionaryEntry *t = nullptr;
  jclass class_hashmap = env->FindClass("java/util/HashMap");
  jmethodID hashmap_init = env->GetMethodID(class_hashmap, "<init>", "()V");
  jobject map = env->NewObject(class_hashmap, hashmap_init);
  jmethodID hashMap_put = env->GetMethodID(
      class_hashmap, "put",
      "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
  while ((t = av_dict_get(d, "", t, AV_DICT_IGNORE_SUFFIX))) {
    jstring key = env->NewStringUTF(t->key);
    jstring value = env->NewStringUTF(t->value);
    env->CallObjectMethod(map, hashMap_put, key, value);
    env->DeleteLocalRef(key);
    env->DeleteLocalRef(value);
  }
  return map;
}

JNIEXPORT jlong JNICALL
Java_soko_ekibun_ffmpeg_AvFormat_initNative(
    JNIEnv *env,
    jobject thiz,
    jstring url
) {
  auto ctx = avformat_alloc_context();
  ctx->opaque = env->NewGlobalRef(thiz);
  ctx->io_open = [](AVFormatContext *s, AVIOContext **pb, const char *url,
                    int flags, AVDictionary **options
  ) {
    auto vm = (JavaVM *) av_jni_get_java_vm(nullptr);
    JNIEnv *env;
    vm->GetEnv((void **) &env, JNI_VERSION_1_4);
    auto thiz = (jobject) s->opaque;
    jclass cls = env->GetObjectClass(thiz);
    jobject io = env->GetObjectField(
        thiz,
        env->GetFieldID(cls, "io", "Lsoko/ekibun/ffmpeg/AvIOContext$Handler;"));
    jmethodID method = env->GetMethodID(
        env->GetObjectClass(io),
        "open",
        "(Ljava/lang/String;)Lsoko/ekibun/ffmpeg/AvIOContext;");
    jstring strUrl = env->NewStringUTF(url);
    jobject ioCtx = env->CallObjectMethod(io, method, strUrl);
    env->DeleteLocalRef(strUrl);
    long bufferSize = env->CallLongMethod(
        ioCtx,
        env->GetMethodID(env->GetObjectClass(ioCtx), "getBufferSize",
                         "()J"));
    *pb = avio_alloc_context(
        (unsigned char *) av_malloc(bufferSize),
        bufferSize,
        0,
        env->NewGlobalRef(ioCtx),
        [](void *opaque, uint8_t *buf, int buf_size) -> int {
          auto vm = (JavaVM *) av_jni_get_java_vm(nullptr);
          JNIEnv *env;
          vm->GetEnv((void **) &env, JNI_VERSION_1_4);
          auto ioCtx = (jobject) opaque;
          jmethodID method = env->GetMethodID(
              env->GetObjectClass(ioCtx),
              "read",
              "([B)I");
          jbyteArray arr = env->NewByteArray(buf_size);
          int ret = env->CallIntMethod(ioCtx, method, arr);
          env->GetByteArrayRegion(arr, 0, ret, (jbyte *) buf);
          env->DeleteLocalRef(arr);
          return ret;
        },
        nullptr,
        [](void *opaque, int64_t offset, int whence) -> int64_t {
          auto vm = (JavaVM *) av_jni_get_java_vm(nullptr);
          JNIEnv *env;
          vm->GetEnv((void **) &env, JNI_VERSION_1_4);
          auto ioCtx = (jobject) opaque;
          jmethodID method = env->GetMethodID(
              env->GetObjectClass(ioCtx),
              "seek",
              "(II)I");
          return env->CallIntMethod(ioCtx, method, offset, whence);
        }
    );
    return 0;
  };
  ctx->io_close = [](AVFormatContext *s, AVIOContext *pb) {
    auto vm = (JavaVM *) av_jni_get_java_vm(nullptr);
    JNIEnv *env;
    vm->GetEnv((void **) &env, JNI_VERSION_1_4);
    auto ioCtx = (jobject) pb->opaque;
    env->DeleteGlobalRef(ioCtx);
  };
  ctx->flags |= AVFMT_FLAG_CUSTOM_IO;
  jboolean copy;
  avformat_open_input(&ctx, env->GetStringUTFChars(url, &copy), nullptr, nullptr);
  return (jlong) ctx;
}

JNIEXPORT jobjectArray JNICALL
Java_soko_ekibun_ffmpeg_AvFormat_getStreamsNative(JNIEnv *env, jobject thiz, jlong pctx) {
  auto ctx = (AVFormatContext *) pctx;
  if (avformat_find_stream_info(ctx, nullptr) != 0)
    env->Throw(env->ExceptionOccurred());
  jclass streamClass = env->FindClass("soko/ekibun/ffmpeg/AvStream");
  jmethodID constructor = env->GetMethodID(streamClass, "<init>", "(JIILjava/util/Map;)V");
  jobjectArray streams = env->NewObjectArray(ctx->nb_streams, streamClass, nullptr);
  for (int i = 0; i < ctx->nb_streams; ++i) {
    AVStream *stream = ctx->streams[i];
    jobject metadata = av_dict_to_java(env, stream->metadata);
    jobject streamObj = env->NewObject(
        streamClass, constructor,
        (jlong) stream, i, (jint) stream->codecpar->codec_type, metadata);
    env->DeleteLocalRef(metadata);
    env->SetObjectArrayElement(
        streams, i, streamObj);
    env->DeleteLocalRef(streamObj);
  }
  return streams;
}

JNIEXPORT void JNICALL
Java_soko_ekibun_ffmpeg_AvFormat_destroyNative(JNIEnv *env, jobject thiz, jlong pctx) {
  auto ctx = (AVFormatContext *) pctx;
  env->DeleteGlobalRef((jobject) ctx->opaque);
  avformat_close_input(&ctx);
}

}
