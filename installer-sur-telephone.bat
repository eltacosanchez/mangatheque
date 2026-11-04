@echo off
chcp 65001 >nul
rem Installe la dernière version compilée de Ma Mangathèque sur le téléphone, par Wi-Fi (ou par câble).
rem Prérequis : téléphone sur le même Wi-Fi que le PC, « Débogage sans fil » activé
rem (Paramètres > Système > Options pour les développeurs). L'association n'est à faire qu'une fois.
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set APK=%~dp0app\build\outputs\apk\release\app-release.apk

if not exist "%APK%" (
  echo Aucun APK compilé trouvé. Lance d'abord : gradlew.bat assembleRelease
  pause
  exit /b 1
)

echo Recherche du téléphone sur le Wi-Fi...
for /f "tokens=3" %%A in ('"%ADB%" mdns services ^| findstr /c:"_adb-tls-connect"') do "%ADB%" connect %%A >nul 2>&1

"%ADB%" get-state >nul 2>&1
if errorlevel 1 (
  echo.
  echo Téléphone introuvable. Vérifie qu'il est sur le Wi-Fi de la maison et que « Débogage sans fil » est activé.
  pause
  exit /b 1
)

echo Installation sur le téléphone (tes données sont conservées)...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo.
  echo L'installation a échoué. Déverrouille le téléphone et réessaie.
  pause
  exit /b 1
)
echo Lancement de l'application...
"%ADB%" shell monkey -p fr.mangatheque.app -c android.intent.category.LAUNCHER 1 >nul
echo C'est fait !
timeout /t 3 >nul
