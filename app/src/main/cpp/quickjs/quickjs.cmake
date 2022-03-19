cmake_minimum_required(VERSION 3.7 FATAL_ERROR)

# quickjs
set(QUICK_JS_LIB_DIR ${CMAKE_CURRENT_LIST_DIR}/quickjs)
file (STRINGS "${QUICK_JS_LIB_DIR}/VERSION" QUICKJS_VERSION)
add_library(quickjs SHARED
  ${CMAKE_CURRENT_LIST_DIR}/quickjs.cpp
  ${QUICK_JS_LIB_DIR}/cutils.c
  ${QUICK_JS_LIB_DIR}/libbf.c
  ${QUICK_JS_LIB_DIR}/libregexp.c
  ${QUICK_JS_LIB_DIR}/libunicode.c
  ${QUICK_JS_LIB_DIR}/quickjs.c
  ${QUICK_JS_LIB_DIR}/unicode_gen.c
)

project(quickjs LANGUAGES CXX)
target_compile_features(quickjs PUBLIC cxx_std_17)
target_compile_options(quickjs PRIVATE "-DCONFIG_VERSION=\"${QUICKJS_VERSION}\"")
target_compile_options(quickjs PRIVATE "-DDUMP_LEAKS")

target_link_libraries(quickjs PRIVATE
  ${common-lib}
)