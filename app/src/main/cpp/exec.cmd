@echo off

:: Check for required tools
if defined MSYS2_BIN (
    if exist %MSYS2_BIN% goto exec
)

echo:
echo MSYS2 is needed. Set it up properly and provide the executable path in MSYS2_BIN environment variable. E.g.
echo:
echo     set MSYS2_BIN="C:\msys64\usr\bin\bash.exe"
echo:
echo See https://trac.ffmpeg.org/wiki/CompilationGuide/WinRT#PrerequisitesandFirstTimeSetupInstructions
exit 1

:exec

set MSYS2_PATH_TYPE=inherit
%MSYS2_BIN% --login -c "cd %cd:\=/% && %*"