@echo off
rem SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
rem SPDX-License-Identifier: Apache-2.0

setlocal
echo Tsuyomi Gradle resource mode: LOW (--max-workers=2 --no-parallel --no-daemon)
call "%~dp0..\gradlew.bat" --max-workers=2 --no-parallel --no-daemon %*
