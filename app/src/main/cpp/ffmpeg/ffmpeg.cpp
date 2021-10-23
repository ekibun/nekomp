#include <jni.h>
#include <map>
#include "../jni_wrap.hpp"

extern "C"
{
#include "libavcodec/jni.h"
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
#include <jni.h>
}

std::map<jobject, jobject> av_dict_to_map(JNIEnv *env, AVDictionary *d) {
  AVDictionaryEntry *t = nullptr;
  std::map<jobject, jobject> map;
  while ((t = av_dict_get(d, "", t, AV_DICT_IGNORE_SUFFIX))) {
    map[env->NewStringUTF(t->key)] = env->NewStringUTF(t->value);
  }
  return map;
}

#ifdef ANDROID

#include <android/log.h>

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

#endif

JavaVM *javaVm;

extern "C"
JNIEXPORT jint JNI_OnLoad(
    JavaVM *vm,
    void *res
) {
#ifdef ANDROID
  av_log_set_callback(android_log_callback);
  av_jni_set_java_vm(vm, nullptr);
#endif
  javaVm = vm;
  return JNI_VERSION_1_4;
}

extern "C"
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
    JNIEnv *env;
    javaVm->GetEnv((void **) &env, JNI_VERSION_1_4);
    auto thiz = (jobject) s->opaque;
    jclass cls = env->GetObjectClass(thiz);
    jobject io = env->GetObjectField(
        thiz,
        env->GetFieldID(cls, "io", "Lsoko/ekibun/ffmpeg/AvIO$Handler;"));
    jmethodID method = env->GetMethodID(
        env->GetObjectClass(io),
        "open",
        "(Ljava/lang/String;)Lsoko/ekibun/ffmpeg/AvIO;");
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
          JNIEnv *env;
          javaVm->GetEnv((void **) &env, JNI_VERSION_1_4);
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
          JNIEnv *env;
          javaVm->GetEnv((void **) &env, JNI_VERSION_1_4);
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
    JNIEnv *env;
    javaVm->GetEnv((void **) &env, JNI_VERSION_1_4);
    auto ioCtx = (jobject) pb->opaque;
    jmethodID method = env->GetMethodID(
        env->GetObjectClass(ioCtx),
        "close",
        "()V");
    env->CallVoidMethod(ioCtx, method);
    env->DeleteGlobalRef(ioCtx);
  };
  ctx->flags |= AVFMT_FLAG_CUSTOM_IO;
  jboolean copy;
  avformat_open_input(&ctx, env->GetStringUTFChars(url, &copy), nullptr, nullptr);
  return (jlong) ctx;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_soko_ekibun_ffmpeg_AvFormat_getStreamsNative(JNIEnv *env, jobject thiz, jlong pctx) {
  auto ctx = (AVFormatContext *) pctx;
  if (avformat_find_stream_info(ctx, nullptr) != 0)
    return nullptr;
  jclass streamClass = env->FindClass("soko/ekibun/ffmpeg/AvStream");
  jmethodID constructor = env->GetMethodID(streamClass, "<init>", "(JIIIIIIJLjava/util/Map;)V");
  jobjectArray streams = env->NewObjectArray(ctx->nb_streams, streamClass, nullptr);
  for (int i = 0; i < ctx->nb_streams; ++i) {
    AVStream *stream = ctx->streams[i];
    auto metadata = av_dict_to_map(env, stream->metadata);
    jobject metadataObj = jniWrapMap(env, metadata);
    jobject streamObj = env->NewObject(
        streamClass, constructor,
        (jlong) stream, i,
        (jint) stream->codecpar->codec_type,
        (jint) stream->codecpar->sample_rate,
        (jint) stream->codecpar->channels,
        (jint) stream->codecpar->width,
        (jint) stream->codecpar->height,
        (jlong) (stream->duration * av_q2d(stream->time_base) * AV_TIME_BASE),
        metadataObj);
    env->DeleteLocalRef(metadataObj);
    env->SetObjectArrayElement(
        streams, i, streamObj);
    env->DeleteLocalRef(streamObj);
  }
  return streams;
}

extern "C"
JNIEXPORT void JNICALL
Java_soko_ekibun_ffmpeg_AvFormat_destroyNative(JNIEnv *env, jobject thiz, jlong pctx) {
  auto ctx = (AVFormatContext *) pctx;
  env->DeleteGlobalRef((jobject) ctx->opaque);
  avformat_close_input(&ctx);
}

extern "C"
JNIEXPORT jint JNICALL
Java_soko_ekibun_ffmpeg_AvFormat_seekToNative(JNIEnv *env, jobject thiz, jlong pctx, jlong ts,
                                              jint stream_index, jlong min_ts, jlong max_ts,
                                              jint flags) {
  return avformat_seek_file((AVFormatContext *) pctx, stream_index, min_ts, ts, max_ts, flags);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_ffmpeg_AvPacket_initNative(JNIEnv *env, jobject thiz) {
  return (jlong) av_packet_alloc();
}
extern "C"
JNIEXPORT void JNICALL
Java_soko_ekibun_ffmpeg_AvPacket_closeNative(JNIEnv *env, jobject thiz, jlong ptr) {
  return av_packet_free((AVPacket **) &ptr);
}

extern "C"
JNIEXPORT jint JNICALL
Java_soko_ekibun_ffmpeg_AvFormat_getPacketNative(JNIEnv *env, jobject thiz, jlong ctx,
                                                 jlong packet) {
  int ret = av_read_frame((AVFormatContext *) ctx, (AVPacket *) packet);
  return ret ? fmin(ret, -1) : ((AVPacket *) packet)->stream_index;
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_ffmpeg_AvCodec_initNative(JNIEnv *env, jobject thiz, jlong stream) {
  auto pstream = (AVStream *) stream;
  auto pCodec = avcodec_find_decoder(pstream->codecpar->codec_id);
  if (!pCodec)
    return 0;
  AVCodecContext *ctx = avcodec_alloc_context3(pCodec);
  int ret = avcodec_parameters_to_context(ctx, pstream->codecpar);
  if (ret == 0) {
    ret = avcodec_open2(ctx, pCodec, nullptr);
    if (ret == 0)
      return (jlong) ctx;
  }
  if (ctx)
    avcodec_free_context(&ctx);
  return 0;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_soko_ekibun_ffmpeg_AvCodec_sendPacketAndGetFrameNative(JNIEnv *env, jobject thiz, jlong pctx,
                                                            jlong stream, jlong packet) {
  auto ctx = (AVCodecContext *) pctx;
  auto frame = (AVFrame *) ctx->opaque;
  if (!frame)
    ctx->opaque = frame = av_frame_alloc();
  if (avcodec_send_packet(ctx, (AVPacket *) packet) == 0 &&
      avcodec_receive_frame(ctx, frame) == 0) {
    ctx->opaque = nullptr;
    jclass cls = env->FindClass("soko/ekibun/ffmpeg/AvFrame");
    jmethodID constructor = env->GetMethodID(cls, "<init>", "(JJII)V");
    jobject frameObj = env->NewObject(
        cls, constructor,
        (jlong) frame,
        (jlong) (frame->best_effort_timestamp * av_q2d(((AVStream *)stream)->time_base) * AV_TIME_BASE),
        (jint) frame->width,
        (jint) frame->height);
    return frameObj;
  }
  return nullptr;
}
extern "C"
JNIEXPORT void JNICALL
Java_soko_ekibun_ffmpeg_AvCodec_flushNative(JNIEnv *env, jobject thiz, jlong ctx) {
  avcodec_flush_buffers((AVCodecContext *) ctx);
}
extern "C"
JNIEXPORT void JNICALL
Java_soko_ekibun_ffmpeg_AvCodec_closeNative(JNIEnv *env, jobject thiz, jlong pctx) {
  auto ctx = (AVCodecContext *) pctx;
  if (ctx->opaque)
    av_frame_free((AVFrame **) &(ctx->opaque));
  avcodec_free_context(&ctx);
}

extern "C"
JNIEXPORT void JNICALL
Java_soko_ekibun_ffmpeg_AvFrame_closeNative(JNIEnv *env, jobject thiz, jlong ptr) {
  av_frame_free((AVFrame **) &ptr);
}

struct SWContext {
  double speedRatio = 1;
  // audio
  int64_t sampleRate = 0;
  int64_t channels = 0;
  int64_t audioFormat = AV_SAMPLE_FMT_NONE;
  uint8_t *audioBuffer = nullptr;
  int64_t audioBufferSize = 0;
  // video
  int64_t width = 0;
  int64_t height = 0;
  int64_t videoFormat = AV_SAMPLE_FMT_NONE;
  uint8_t *videoBuffer = nullptr;
  int64_t videoBufferSize = 0;
  // opaque
  SwrContext *_swrCtx = nullptr;
  int64_t _srcChannelLayout = 0;
  AVSampleFormat _srcAudioFormat = AV_SAMPLE_FMT_NONE;
  int64_t _srcSampleRate = 0;
  uint8_t *_audioBuffer1 = nullptr;
  unsigned int _audioBufferLen = 0;
  unsigned int _audioBufferLen1 = 0;
  SwsContext *_swsCtx = nullptr;
  AVPixelFormat _srcVideoFormat = AV_PIX_FMT_NONE;
  unsigned int _videoBufferLen = 0;
  uint8_t *_videoData[4]{};
  int _linesize[4]{};
};

int64_t postFrameAudio(SWContext *ctx, AVFrame *frame) {
  if (ctx->audioFormat == AV_SAMPLE_FMT_NONE)
    return -1;
  int sampleRate = (int) round(frame->sample_rate * ctx->speedRatio);
  if (!ctx->_swrCtx ||
      ctx->_srcChannelLayout != frame->channel_layout ||
      ctx->_srcAudioFormat != frame->format ||
      ctx->_srcSampleRate != sampleRate) {
    if (ctx->_swrCtx)
      swr_free(&ctx->_swrCtx);
    ctx->_swrCtx = swr_alloc_set_opts(
        nullptr,
        av_get_default_channel_layout(ctx->channels),
        (AVSampleFormat) ctx->audioFormat,
        ctx->sampleRate,
        frame->channel_layout,
        (AVSampleFormat) frame->format,
        sampleRate, 0, nullptr);
    if (!ctx->_swrCtx || swr_init(ctx->_swrCtx) < 0)
      return -1;
    ctx->_srcChannelLayout = frame->channel_layout;
    ctx->_srcAudioFormat = (AVSampleFormat) frame->format;
    ctx->_srcSampleRate = sampleRate;
  }
  int inCount = frame->nb_samples;
  int outCount = (int) (inCount * ctx->sampleRate / sampleRate + 256);
  int outSize = av_samples_get_buffer_size(
      nullptr, ctx->channels, outCount, (AVSampleFormat) ctx->audioFormat, 0);
  if (outSize < 0)
    return -2;
  av_fast_malloc(&ctx->_audioBuffer1, &ctx->_audioBufferLen, outSize);
  if (!ctx->_audioBuffer1)
    return -3;
  int frameCount =
      swr_convert(ctx->_swrCtx, &ctx->_audioBuffer1, outCount,
                  (const uint8_t **) frame->extended_data, inCount);
  ctx->audioBufferSize =
      frameCount * av_get_bytes_per_sample((AVSampleFormat) ctx->audioFormat) * ctx->channels;
  uint8_t *buffer = ctx->_audioBuffer1;
  unsigned int bufferLen = ctx->_audioBufferLen1;
  ctx->_audioBuffer1 = ctx->audioBuffer;
  ctx->_audioBufferLen1 = ctx->_audioBufferLen;
  ctx->audioBuffer = buffer;
  ctx->_audioBufferLen = bufferLen;
  return 0;
}

int64_t postFrameVideo(SWContext *ctx, AVFrame *frame) {
  if (!ctx->_swsCtx ||
      ctx->width != frame->width ||
      ctx->height != frame->height ||
      ctx->_srcVideoFormat != frame->format) {
    if (ctx->_swsCtx)
      sws_freeContext(ctx->_swsCtx);
    ctx->_swsCtx = nullptr;
    ctx->videoBufferSize = av_image_get_buffer_size((AVPixelFormat) ctx->videoFormat, frame->width,
                                                    frame->height, 1);
    if (!ctx->videoBufferSize)
      return -1;
    ctx->width = frame->width;
    ctx->height = frame->height;
    av_fast_malloc(&ctx->videoBuffer, &ctx->_videoBufferLen, ctx->videoBufferSize);
    if (!ctx->videoBuffer)
      return -1;
    av_image_fill_arrays(
        ctx->_videoData,
        ctx->_linesize,
        ctx->videoBuffer,
        (AVPixelFormat) ctx->videoFormat,
        ctx->width,
        ctx->height, 1);
    ctx->_swsCtx = sws_getContext(
        frame->width,
        frame->height,
        (AVPixelFormat) frame->format,
        ctx->width,
        ctx->height,
        AV_PIX_FMT_RGBA,
        SWS_POINT,
        nullptr, nullptr, nullptr);
  }
  if (!ctx->_swsCtx)
    return -1;
  sws_scale(
      ctx->_swsCtx,
      frame->data,
      frame->linesize,
      0,
      frame->height,
      ctx->_videoData,
      ctx->_linesize);
  return 0;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_ffmpeg_AvPlayback_initNative(JNIEnv *env, jobject thiz, jint sample_rate,
                                              jint channels, jint audio_format, jint video_format) {
  auto ret = new SWContext();
  ret->speedRatio = 1;
  ret->sampleRate = sample_rate;
  ret->channels = channels;
  ret->audioFormat = audio_format;
  ret->videoFormat = video_format;
  return (jlong) ret;
}
extern "C"
JNIEXPORT jint JNICALL
Java_soko_ekibun_ffmpeg_AvPlayback_postFrameNative(JNIEnv *env, jobject thiz, jlong ctx,
                                                   jint codec_type, jlong frame) {
  switch (codec_type) {
    case AVMEDIA_TYPE_AUDIO:
      return postFrameAudio((SWContext *) ctx, (AVFrame *) frame);
    case AVMEDIA_TYPE_VIDEO:
      return postFrameVideo((SWContext *) ctx, (AVFrame *) frame);
    default:
      return -1;
  }
}
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_soko_ekibun_ffmpeg_AvPlayback_getBuffer(JNIEnv *env, jobject thiz, jlong pctx,
                                             jint codec_type) {
  auto ctx = (SWContext *) pctx;
  jbyteArray arr;
  switch (codec_type) {
    case AVMEDIA_TYPE_AUDIO:
      arr = env->NewByteArray(ctx->audioBufferSize);
      env->SetByteArrayRegion(arr, 0, ctx->audioBufferSize, (jbyte *) ctx->audioBuffer);
      return arr;
    case AVMEDIA_TYPE_VIDEO:
      arr = env->NewByteArray(ctx->videoBufferSize);
      env->SetByteArrayRegion(arr, 0, ctx->videoBufferSize, (jbyte *) ctx->videoBuffer);
      return arr;
    default:
      return nullptr;
  }
}
extern "C"
JNIEXPORT void JNICALL
Java_soko_ekibun_ffmpeg_AvPlayback_closeNative(JNIEnv *env, jobject thiz, jlong pctx) {
  auto ctx = (SWContext *) pctx;
  if(!ctx) return;
  if(ctx->_swsCtx) sws_freeContext(ctx->_swsCtx);
  if(ctx->_swrCtx) swr_free(&ctx->_swrCtx);
  delete ctx;
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_soko_ekibun_ffmpeg_AvPlayback_speedRatioNative(JNIEnv *env, jobject thiz, jlong pctx,
                                                    jfloat new_value) {
  auto ctx = (SWContext *) pctx;
  if(!ctx) return 1;
  if(new_value > 0) ctx->speedRatio = new_value;
  return ctx->speedRatio;
}