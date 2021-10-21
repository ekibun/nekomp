cmake_minimum_required(VERSION 3.10.2)

project("nekomp")

find_library(log-lib log)
set(common-lib ${common-lib} ${log-lib})
include("${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg/CMakeLists.txt")
