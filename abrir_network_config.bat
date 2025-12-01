@echo off
REM ========================================
REM Script para abrir NetworkConfigActivity
REM ========================================

echo.
echo ======================================
echo   Abriendo Configuracion de Red
echo ======================================
echo.

REM Verificar que ADB este disponible
where adb >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] ADB no esta en el PATH.
    echo.
    echo Buscando ADB en Android Studio...

    REM Intentar encontrar ADB en la ubicacion por defecto de Android Studio
    set "ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

    if exist "%ADB_PATH%" (
        echo [OK] ADB encontrado en: %ADB_PATH%
        set "ADB=%ADB_PATH%"
    ) else (
        echo [ERROR] No se encontro ADB.
        echo.
        echo Soluciones:
        echo 1. Instala Android Studio
        echo 2. Descarga ADB de: https://developer.android.com/tools/releases/platform-tools
        echo 3. Agrega ADB al PATH de Windows
        echo.
        pause
        exit /b 1
    )
) else (
    set "ADB=adb"
)

REM Verificar dispositivos conectados
echo Verificando dispositivos conectados...
%ADB% devices

REM Contar dispositivos (excluyendo la primera linea)
for /f "skip=1 tokens=*" %%a in ('%ADB% devices 2^>nul') do set "DEVICE_LINE=%%a"

if "%DEVICE_LINE%"=="" (
    echo.
    echo [ERROR] No se detectaron dispositivos.
    echo.
    echo Soluciones:
    echo 1. Conecta el telefono por USB
    echo 2. Habilita Depuracion USB en el telefono
    echo 3. Acepta el dialogo de depuracion USB
    echo.
    pause
    exit /b 1
)

echo.
echo [OK] Dispositivo detectado
echo.

REM Abrir NetworkConfigActivity
echo Abriendo NetworkConfigActivity...
%ADB% shell am start -n com.mjc.mascotalink/.NetworkConfigActivity

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ======================================
    echo   [EXITO] Pantalla abierta
    echo ======================================
    echo.
    echo La pantalla de Configuracion de Red
    echo se abrio en tu dispositivo.
    echo.
) else (
    echo.
    echo [ERROR] No se pudo abrir la actividad.
    echo.
    echo Posibles causas:
    echo 1. La app no esta instalada
    echo 2. El nombre del paquete es incorrecto
    echo.
    echo Verifica que la app este instalada:
    echo %ADB% shell pm list packages ^| findstr mascotalink
    echo.
)

pause
