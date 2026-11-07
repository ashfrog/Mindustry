@echo off
setlocal
title Pinyin Escape - MindustryX

rem Always run from the project folder, even when started by double-clicking.
cd /d "%~dp0"

if not exist "%~dp0gradlew.bat" (
    echo gradlew.bat was not found. Keep this file in the MindustryX root folder.
    pause
    exit /b 1
)

echo Starting Pinyin Escape. The first launch may take a little while to compile...
call "%~dp0gradlew.bat" desktop:run --no-daemon
set "exitCode=%errorlevel%"

if not "%exitCode%"=="0" (
    echo.
    echo Game failed to start. Exit code: %exitCode%
    pause
)

exit /b %exitCode%
