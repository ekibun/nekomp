@echo off
set MSYS2_PATH_TYPE=inherit
set ANDROID_NDK_HOME=D:/Apps/AndroidSdk/ndk/22.1.7171670
"D:\Apps\msys64\usr\bin\bash.exe" --login %~dp0build.sh