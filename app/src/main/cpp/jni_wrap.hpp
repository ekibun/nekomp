#include <jni.h>
#include <map>

jobject jniWrapMap(JNIEnv *env, std::map<jobject, jobject> val)
{
  jclass class_hashmap = env->FindClass("java/util/HashMap");
  jmethodID hashmap_init = env->GetMethodID(class_hashmap, "<init>", "()V");
  jobject map = env->NewObject(class_hashmap, hashmap_init);
  jmethodID hashMap_put = env->GetMethodID(class_hashmap, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
  for (auto it = val.begin(); it != val.end(); ++it)
  {
    env->CallObjectMethod(map, hashMap_put, it->first, it->second);
    env->DeleteLocalRef(it->first);
    env->DeleteLocalRef(it->second);
  }
  return map;
}