@echo off
chcp 65001 >nul
rem Installe la dernière version compilée de Ma Mangathèque sur le téléphone branché en USB.
rem Prérequis (une seule fois) : « Débogage USB » activé sur le téléphone.
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set APK=%~dp0app\build\outputs\apk\release\app-release.apk

if not exist "%APK%" (
  echo Aucun APK compilé trouvé. Lance d'abord : gradlew.bat assembleRelease
  pause
  exit /b 1
)

echo Recherche du téléphone...
"%ADB%" wait-for-device
echo Installation sur le téléphone (tes données sont conservées)...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo.
  echo L'installation a échoué. Vérifie que le téléphone est déverrouillé et que tu as accepté « Autoriser le débogage USB ».
  pause
  exit /b 1
)
echo Lancement de l'application...
"%ADB%" shell monkey -p fr.mangatheque.app -c android.intent.category.LAUNCHER 1 >nul
echo C'est fait !
timeout /t 3 >nul
