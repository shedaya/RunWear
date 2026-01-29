$adb = "C:\Users\solhe\AppData\Local\Android\Sdk\platform-tools\adb.exe"

Write-Host "Checking connected devices..."
& $adb devices -l

Write-Host ""
Write-Host "Installing phone app..."
& $adb -d install -r "C:\androidprojects\RunWear\RunWear-android\app\build\outputs\apk\debug\app-debug.apk"

Write-Host ""
Write-Host "Installing watch app..."
& $adb -e install -r "C:\androidprojects\RunWear\RunWear-android\wear\build\outputs\apk\debug\wear-debug.apk"

Write-Host ""
Write-Host "Starting phone app..."
& $adb -d shell am start -n com.runwear.app/.MainActivity

Write-Host ""
Write-Host "Starting watch app..."
& $adb -e shell am start -n com.runwear.app/.presentation.MainActivity

Write-Host ""
Write-Host "Done!"
