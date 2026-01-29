$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Set-Location "C:\androidprojects\RunWear\RunWear-android"
& ./gradlew.bat :app:assembleDebug :wear:assembleDebug --no-daemon
