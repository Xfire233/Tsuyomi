@echo off
rem SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
rem SPDX-License-Identifier: Apache-2.0

setlocal
if not defined NUMBER_OF_PROCESSORS set "NUMBER_OF_PROCESSORS=1"
echo Tsuyomi Gradle resource mode: HIGH (--max-workers=%NUMBER_OF_PROCESSORS% --parallel)
call "%~dp0..\gradlew.bat" --max-workers=%NUMBER_OF_PROCESSORS% --parallel %*
exit /b %ERRORLEVEL%
