@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
cd /d C:\androidprojects\RunWear\RunWear-android
call gradlew.bat :app:assembleDebug > build_output.txt 2>&1
echo BUILD_DONE
