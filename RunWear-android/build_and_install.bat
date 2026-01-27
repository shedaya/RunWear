@echo off
cd /d C:\androidprojects\RunWear\RunWear-android
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%PATH%;C:\Users\solhe\AppData\Local\Android\Sdk\platform-tools

echo Building phone app...
call gradlew.bat :app:assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo Phone app build FAILED
    exit /b 1
)

echo Building wear app...
call gradlew.bat :wear:assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo Wear app build FAILED
    exit /b 1
)

echo.
echo Installing phone app...
adb -s RFCY61FQFDD install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo Phone install FAILED
)

echo.
echo Installing wear app...
adb -s adb-59131WRBNL308R-6BVhwT._adb-tls-connect._tcp install -r wear\build\outputs\apk\debug\wear-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo Wear install FAILED - trying alternate device...
    for /f "tokens=1" %%i in ('adb devices ^| findstr /v "List" ^| findstr /v "RFCY61FQFDD"') do (
        echo Trying %%i
        adb -s %%i install -r wear\build\outputs\apk\debug\wear-debug.apk
    )
)

echo.
echo Done!
