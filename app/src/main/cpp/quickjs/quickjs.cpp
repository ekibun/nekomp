#include <jni.h>
#include "quickjs/quickjs.h"
#include <unordered_map>
#include <cstring>

extern "C"
{
#include "quickjs/libregexp.h"
}

struct JSRuntimeOpaque {
  JavaVM *javaVm;
  jobject thiz;
  JSClassID javaClassID;
};

void jsFreeValue(jlong ctx, jlong obj) {
  JS_FreeValue((JSContext *) ctx, *((JSValue *) obj));
  delete (JSValue *) obj;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_initContext(JNIEnv *env, jclass, jobject ctx) {
  JavaVM *javaVm;
  env->GetJavaVM(&javaVm);
  JSRuntime *rt = JS_NewRuntime();
  auto opaque = new JSRuntimeOpaque{
      javaVm,
      env->NewWeakGlobalRef(ctx),
      0
  };
  JS_SetModuleLoaderFunc(
      rt, nullptr, [](JSContext *ctx, const char *module_name, void *) -> JSModuleDef * {
        auto rt = JS_GetRuntime(ctx);
        auto opaque = (JSRuntimeOpaque *) JS_GetRuntimeOpaque(rt);
        if (opaque == nullptr)
          return nullptr;
        JNIEnv *env;
        opaque->javaVm->GetEnv((void **) &env, JNI_VERSION_1_4);
        jclass clazz = env->GetObjectClass(opaque->thiz);
        jmethodID load = env->GetMethodID(clazz, "loadModule",
                                          "(Ljava/lang/String;)Ljava/lang/String;");
        auto javaStr = env->NewStringUTF(module_name);
        auto retJava = (jstring) env->CallObjectMethod(opaque->thiz, load, javaStr);
        if (retJava == nullptr)
          return nullptr;
        env->DeleteLocalRef(javaStr);
        auto str = env->GetStringUTFChars(retJava, nullptr);
        JSValue func_val = JS_Eval(ctx, str, strlen(str), module_name,
                                   JS_EVAL_TYPE_MODULE | JS_EVAL_FLAG_COMPILE_ONLY);
        env->ReleaseStringUTFChars(retJava, str);
        env->DeleteLocalRef(retJava);
        if (JS_IsException(func_val))
          return nullptr;
        /* the module is already referenced, so we must free it */
        auto m = (JSModuleDef *) JS_VALUE_GET_PTR(func_val);
        JS_FreeValue(ctx, func_val);
        return m;
      }, nullptr);
  JS_SetRuntimeOpaque(rt, opaque);
  JS_NewClassID(&(opaque->javaClassID));
  if (!JS_IsRegisteredClass(rt, opaque->javaClassID)) {
    JSClassDef def{
        "JavaObject",
        // destructor
        [](JSRuntime *rt, JSValue obj) noexcept {
          auto opaque = (JSRuntimeOpaque *) JS_GetRuntimeOpaque(rt);
          if (opaque == nullptr)
            return;
          JNIEnv *env;
          opaque->javaVm->GetEnv((void **) &env, JNI_VERSION_1_4);
          env->DeleteGlobalRef((jobject) JS_GetOpaque(obj, opaque->javaClassID));
        }};
    int e = JS_NewClass(rt, opaque->javaClassID, &def);
    if (e < 0) {
      JS_FreeRuntime(rt);
      return 0;
    }
  }
  return (jlong) JS_NewContext(rt);
}
extern "C"
JNIEXPORT void JNICALL
Java_soko_ekibun_quickjs_QuickJS_destroyContext(JNIEnv *, jclass, jlong ctx) {
  JSRuntime *rt = JS_GetRuntime((JSContext *) ctx);
  JS_FreeContext((JSContext *) ctx);
  JS_SetRuntimeOpaque(rt, nullptr);
  JS_FreeRuntime(rt);
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsNewError(JNIEnv *, jclass, jlong ctx) {
  return (jlong) new JSValue(JS_NewError((JSContext *) ctx));
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsNewString(JNIEnv *env, jclass, jlong ctx, jstring obj) {
  auto jstr = env->GetStringUTFChars(obj, nullptr);
  auto ret = (jlong) new JSValue(JS_NewString(
      (JSContext *) ctx, jstr));
  env->ReleaseStringUTFChars(obj, jstr);
  return ret;
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsNewBool(JNIEnv *, jclass, jlong ctx, jboolean obj) {
  return (jlong) new JSValue(JS_NewBool((JSContext *) ctx, obj));
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsNewInt64(JNIEnv *, jclass, jlong ctx, jlong obj) {
  return (jlong) new JSValue(JS_NewInt64((JSContext *) ctx, obj));
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsNewFloat64(JNIEnv *, jclass, jlong ctx, jdouble obj) {
  return (jlong) new JSValue(JS_NewFloat64((JSContext *) ctx, obj));
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsNewArrayBuffer(JNIEnv *env, jclass, jlong ctx,
                                                  jbyteArray obj) {
  return (jlong) new JSValue(JS_NewArrayBufferCopy(
      (JSContext *) ctx,
      (uint8_t *) env->GetByteArrayElements(obj, nullptr),
      env->GetArrayLength(obj)));
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsNewObject(JNIEnv *, jclass, jlong ctx) {
  return (jlong) new JSValue(JS_NewObject((JSContext *) ctx));
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsNewArray(JNIEnv *, jclass, jlong ctx) {
  return (jlong) new JSValue(JS_NewArray((JSContext *) ctx));
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsNULL(JNIEnv *, jclass) {
  return (jlong) new JSValue(JS_NULL);
}
extern "C"
JNIEXPORT jint JNICALL
Java_soko_ekibun_quickjs_QuickJS_definePropertyValue(JNIEnv *, jclass, jlong ctx,
                                                     jlong obj, jlong k, jlong v, jint flags) {
  auto atom = JS_ValueToAtom((JSContext *) ctx, *(JSValue *) k);
  auto ret = JS_DefinePropertyValue((JSContext *) ctx, *(JSValue *) obj, atom, *(JSValue *) v,
                                    flags);
  JS_FreeAtom((JSContext *) ctx, atom);
  jsFreeValue(ctx, k);
  delete (JSValue *) v;
  return ret;
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsDupValue(JNIEnv *, jclass, jlong ctx, jlong obj) {
  return (jlong) new JSValue(JS_DupValue((JSContext *) ctx, *(JSValue *) obj));
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_soko_ekibun_quickjs_QuickJS_isException(JNIEnv *, jclass, jlong obj) {
  return JS_IsException(*(JSValue *) obj);
}
extern "C"
JNIEXPORT void JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsFreeValue(JNIEnv *, jclass, jlong ctx, jlong obj) {
  jsFreeValue(ctx, obj);
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_evaluate(JNIEnv *env, jclass, jlong ctx, jstring cmd,
                                          jstring name, jint flag) {
  JS_UpdateStackTop(JS_GetRuntime((JSContext *) ctx));
  return (jlong) new JSValue(JS_Eval((JSContext *) ctx,
                                     env->GetStringUTFChars(cmd, nullptr),
                                     env->GetStringUTFLength(cmd),
                                     env->GetStringUTFChars(name, nullptr),
                                     flag));
}

jstring jsToString(JNIEnv *env, JSContext *ctx, JSValue val) {
  auto pstr = JS_ToCString(ctx, val);
  auto ret = env->NewStringUTF(pstr);
  JS_FreeCString(ctx, pstr);
  return ret;
}

jthrowable jsToThrowable(JNIEnv *env, JSContext *ctx, JSValue val) {
  jclass clazz = env->FindClass("soko/ekibun/quickjs/JSError");
  jmethodID init = env->GetMethodID(clazz, "<init>",
                                    "(Ljava/lang/String;Ljava/lang/String;)V");
  auto atomStack = JS_NewAtom(ctx, "stack");
  jstring stack = nullptr;
  if (JS_HasProperty(ctx, val, atomStack)) {
    auto jsStack = JS_GetProperty(ctx, val, atomStack);
    stack = jsToString(env, ctx, jsStack);
    JS_FreeValue(ctx, jsStack);
  }
  JS_FreeAtom(ctx, atomStack);
  return (jthrowable) env->NewObject(clazz, init, jsToString(env, ctx, val), stack);
}

extern "C"
JNIEXPORT jthrowable JNICALL
Java_soko_ekibun_quickjs_QuickJS_getException(JNIEnv *env, jclass, jlong ctx) {
  auto err = JS_GetException((JSContext *) ctx);
  auto ret = jsToThrowable(env, (JSContext *) ctx, err);
  JS_FreeValue((JSContext *) ctx, err);
  return ret;
}

jobject jsToJava(JNIEnv *env, JSContext *ctx, JSValue obj,
                 std::unordered_map<void *, jobject> cache = std::unordered_map<void *, jobject>()) {
  int tag = JS_VALUE_GET_TAG(obj);
  if (JS_TAG_IS_FLOAT64(tag)) {
    double p;
    JS_ToFloat64(ctx, &p, obj);
    jclass jclazz = env->FindClass("java/lang/Double");
    jmethodID jmethod = env->GetMethodID(jclazz, "<init>", "(D)V");
    return env->NewObject(jclazz, jmethod, p);
  }
  switch (tag) {
    case JS_TAG_BOOL: {
      jclass jclazz = env->FindClass("java/lang/Boolean");
      jmethodID jmethod = env->GetMethodID(jclazz, "<init>", "(Z)V");
      return env->NewObject(jclazz, jmethod, JS_ToBool(ctx, obj));
    }
    case JS_TAG_INT: {
      int64_t p;
      JS_ToInt64(ctx, &p, obj);
      jclass jclazz = env->FindClass("java/lang/Long");
      jmethodID jmethod = env->GetMethodID(jclazz, "<init>", "(J)V");
      return env->NewObject(jclazz, jmethod, p);
    }
    case JS_TAG_STRING: {
      return jsToString(env, ctx, obj);
    }
    case JS_TAG_OBJECT: {
      { // ArrayBuffer
        size_t size;
        uint8_t *buf = JS_GetArrayBuffer(ctx, &size, obj);
        if (buf) {
          jbyteArray arr = env->NewByteArray((jsize) size);
          env->SetByteArrayRegion(arr, 0, (jsize) size, (int8_t *) buf);
          return arr;
        }
      }
      // javaObject
      auto opaque = (JSRuntimeOpaque *) JS_GetRuntimeOpaque(JS_GetRuntime(ctx));
      auto javaObj = JS_GetOpaque(obj, opaque->javaClassID);
      if (javaObj) return (jobject) javaObj;
      // object cache
      auto ptr = JS_VALUE_GET_PTR(obj);
      if (cache.find(ptr) != cache.end())
        return cache[ptr];
      if (JS_IsFunction(ctx, obj)) {
        jclass clazz = env->FindClass("soko/ekibun/quickjs/JSFunction");
        jmethodID init = env->GetMethodID(clazz, "<init>",
                                          "(JLsoko/ekibun/quickjs/QuickJS$Context;)V");
        return env->NewObject(clazz, init, (jlong) new JSValue(JS_DupValue(ctx, obj)),
                              opaque->thiz);
      } else if (JS_IsError(ctx, obj)) {
        return jsToThrowable(env, ctx, obj);
      } else if (JS_IsPromise(ctx, obj)) {
        jclass clazz = env->GetObjectClass(opaque->thiz);
        jmethodID wrap = env->GetMethodID(clazz, "wrapJSPromiseAsync",
                                          "(JLsoko/ekibun/quickjs/JSFunction;)Lkotlinx/coroutines/Deferred;");
        auto thenJs = JS_GetPropertyStr(ctx, obj, "then");
        auto thenJava = jsToJava(env, ctx, thenJs, cache);
        JS_FreeValue(ctx, thenJs);
        auto ret = env->CallObjectMethod(
            opaque->thiz, wrap, (jlong) new JSValue(JS_DupValue(ctx, obj)), thenJava);
        env->DeleteLocalRef(thenJava);
        return ret;
      } else if (JS_IsArray(ctx, obj)) {
        auto jsArrLen = JS_GetPropertyStr(ctx, obj, "length");
        int64_t arrLen;
        JS_ToInt64(ctx, &arrLen, jsArrLen);
        JS_FreeValue(ctx, jsArrLen);
        auto list = env->NewObjectArray((int) arrLen, env->FindClass("java/lang/Object"), nullptr);
        cache[ptr] = list;
        for (int i = 0; i < arrLen; i++) {
          auto jsprop = JS_GetPropertyUint32(ctx, obj, i);
          auto jval = jsToJava(env, ctx, jsprop, cache);
          env->SetObjectArrayElement(list, i, jsToJava(env, ctx, jsprop, cache));
          env->DeleteLocalRef(jval);
          JS_FreeValue(ctx, jsprop);
        }
        return list;
      } else {
        JSPropertyEnum *ptab;
        uint32_t plen;
        if (JS_GetOwnPropertyNames(ctx, &ptab, &plen, obj, -1))
          return nullptr;
        jclass class_hashmap = env->FindClass("java/util/HashMap");
        jmethodID hashmap_init = env->GetMethodID(class_hashmap, "<init>", "()V");
        jobject ret = env->NewObject(class_hashmap, hashmap_init);
        jmethodID hashMap_put = env->GetMethodID(
            class_hashmap, "put",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        cache[ptr] = ret;
        for (uint32_t i = 0; i < plen; i++) {
          auto jskey = JS_AtomToValue(ctx, ptab[i].atom);
          auto key = jsToJava(env, ctx, jskey, cache);
          auto jsvalue = JS_GetProperty(ctx, obj, ptab[i].atom);
          auto value = jsToJava(env, ctx, jsvalue, cache);
          env->CallObjectMethod(ret, hashMap_put, key, value);
          env->DeleteLocalRef(key);
          env->DeleteLocalRef(value);
          JS_FreeValue(ctx, jskey);
          JS_FreeValue(ctx, jsvalue);
          JS_FreeAtom(ctx, ptab[i].atom);
        }
        js_free(ctx, ptab);
        return ret;
      }
    }
    default:
      return nullptr;
  }
}

extern "C"
JNIEXPORT jobject JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsToJava(JNIEnv *env, jclass, jlong ctx, jlong obj) {
  auto ret = jsToJava(env, (JSContext *) ctx, *(JSValue *) obj);
  jsFreeValue(ctx, obj);
  return ret;
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsNewCFunction(JNIEnv *, jclass, jlong ctx, jlong obj) {
  return (jlong) new JSValue(JS_NewCFunctionData(
      (JSContext *) ctx,
      [](JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv, int magic,
         JSValue *func_data) -> JSValue {
        auto rt = JS_GetRuntime(ctx);
        auto opaque = (JSRuntimeOpaque *) JS_GetRuntimeOpaque(rt);
        if (opaque == nullptr)
          return JS_ThrowInternalError(ctx, "Missing RuntimeOpaque");
        JNIEnv *env;
        opaque->javaVm->GetEnv((void **) &env, JNI_VERSION_1_4);
        auto obj = (jobject) JS_GetOpaque(func_data[0], opaque->javaClassID);
        auto clazz = env->GetObjectClass(opaque->thiz);
        jmethodID invoke = env->GetMethodID(
            clazz, "handleJSInvokable",
            "(Lsoko/ekibun/quickjs/JSInvokable;[Ljava/lang/Object;Ljava/lang/Object;)J");
        auto list = env->NewObjectArray((int) argc, env->FindClass("java/lang/Object"), nullptr);
        for (int i = 0; i < argc; ++i) {
          auto v = jsToJava(env, ctx, argv[i]);
          env->SetObjectArrayElement(list, i, v);
          env->DeleteLocalRef(v);
        }
        auto thisJava = jsToJava(env, ctx, this_val);
        auto retPtr = (JSValue *) env->CallLongMethod(opaque->thiz, invoke, obj, list, thisJava);
        env->DeleteLocalRef(list);
        env->DeleteLocalRef(thisJava);
        JSValue ret = *retPtr;
        delete retPtr;
        return ret;
      }, 0, 0, 1, (JSValue *) obj));
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsThrowError(JNIEnv *, jclass, jlong ctx, jlong err) {
  JS_Throw((JSContext *) ctx, *(JSValue *) err);
  jsFreeValue(ctx, err);
  return (jlong) new JSValue(JS_EXCEPTION);
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsWrapObject(JNIEnv *env, jclass, jlong ctx, jobject obj) {
  auto rt = JS_GetRuntime((JSContext *) ctx);
  auto opaque = (JSRuntimeOpaque *) JS_GetRuntimeOpaque(rt);
  auto jsObj = new JSValue(JS_NewObjectClass((JSContext *) ctx, (int) opaque->javaClassID));
  if (!JS_IsException(*jsObj))
    JS_SetOpaque(*jsObj, env->NewGlobalRef(obj));
  return (jlong) jsObj;
}
extern "C"
JNIEXPORT jlong JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsCall(JNIEnv *env, jclass, jlong ctx, jlong obj,
                                        jlong this_val, jint argc, jlongArray argv) {
  JSRuntime *rt = JS_GetRuntime((JSContext *) ctx);
  JS_UpdateStackTop(rt);
  auto argvJs = new JSValue[argc];
  auto longArr = env->GetLongArrayElements(argv, nullptr);
  for (int i = 0; i < argc; ++i) {
    argvJs[i] = *(JSValue *) longArr[i];
  }
  return (jlong) new JSValue(
      JS_Call((JSContext *) ctx, *(JSValue *) obj, *(JSValue *) this_val, argc, argvJs));
}
extern "C"
JNIEXPORT jint JNICALL
Java_soko_ekibun_quickjs_QuickJS_executePendingJob(JNIEnv *, jclass, jlong ctx) {
  auto rt = JS_GetRuntime((JSContext *) ctx);
  JS_UpdateStackTop(rt);
  JSContext *pctx;
  return JS_ExecutePendingJob(rt, &pctx);
}
extern "C"
JNIEXPORT jlongArray JNICALL
Java_soko_ekibun_quickjs_QuickJS_jsNewPromise(JNIEnv *env, jclass, jlong ctx) {
  auto resolving_funcs = new JSValue[2];
  auto promise = (jlong) new JSValue(JS_NewPromiseCapability((JSContext *) ctx, resolving_funcs));
  auto resolve = (jlong) new JSValue(resolving_funcs[0]);
  auto reject = (jlong) new JSValue(resolving_funcs[1]);
  delete[] resolving_funcs;
  auto arr = env->NewLongArray(3);
  env->SetLongArrayRegion(arr, 0, 1, &promise);
  env->SetLongArrayRegion(arr, 1, 1, &resolve);
  env->SetLongArrayRegion(arr, 2, 1, &reject);
  return arr;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_soko_ekibun_quickjs_Highlight_isIdentNext(JNIEnv *, jobject, jchar c) {
  return (jboolean) lre_js_is_ident_first(c);
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_soko_ekibun_quickjs_Highlight_isIdentFirst(JNIEnv *, jobject, jchar c) {
  return (jboolean) lre_js_is_ident_next(c);
}