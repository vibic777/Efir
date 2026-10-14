@echo off
REM ============================================================
REM  Efir — сборка релизных артефактов (Windows + Android)
REM ------------------------------------------------------------
REM  Требования: JDK 17+, Android SDK (local.properties), WiX 3.11
REM  (загружается автоматически плагином Compose Desktop).
REM
REM  Версия задаётся в version.properties (перед релизом её нужно поднять).
REM  Файлы прежней версии из dist\ сборка убирает сама.
REM
REM  Результат — в папке dist\:
REM    Efir-<ver>.msi, Efir-<ver>.exe   установщики Windows
REM    Efir-<ver>-windows.zip           переносимая сборка
REM    Efir-<ver>-release.apk           Android
REM ============================================================
setlocal

echo [1/3] Тесты модуля core...
call gradlew.bat :core:desktopTest --console=plain
if errorlevel 1 goto :fail

echo [2/3] Сборка Windows (MSI/EXE)...
call gradlew.bat :desktopApp:packageInstaller :desktopApp:packageInstallerExe --console=plain
if errorlevel 1 goto :fail

echo [3/4] Сборка Android release...
call gradlew.bat :androidApp:assembleRelease --console=plain
if errorlevel 1 goto :fail

echo [4/4] Подпись Windows-инсталляторов...
call scripts\sign-windows.cmd

REM Подписанные установщики заменяют в dist\ неподписанные копии.
call gradlew.bat collectDist --console=plain
if errorlevel 1 goto :fail

echo.
echo ============================================================
echo  Готово. Все релизы лежат в папке dist\
echo ============================================================
exit /b 0

:fail
echo.
echo СБОРКА НЕ УДАЛАСЬ. См. вывод выше.
exit /b 1
