@echo off
chcp 65001 >nul
rem Affiche l'écran du téléphone dans une fenêtre du PC (pilotable à la souris), par Wi-Fi.
rem Prérequis : téléphone sur le Wi-Fi de la maison, « Débogage sans fil » activé.
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set SCRCPY=%LOCALAPPDATA%\Android\scrcpy\scrcpy.exe

echo Recherche du téléphone sur le Wi-Fi...
set PHONE=
for /f "tokens=3" %%A in ('"%ADB%" mdns services ^| findstr /c:"_adb-tls-connect"') do set PHONE=%%A
if "%PHONE%"=="" (
  echo.
  echo Téléphone introuvable.
  echo Vérifie qu'il est sur le Wi-Fi de la maison et que « Débogage sans fil » est activé
  echo (Paramètres ^> Système ^> Options pour les développeurs).
  pause
  exit /b 1
)
"%ADB%" connect %PHONE% >nul 2>&1
echo Ouverture de l'écran du téléphone...
start "" "%SCRCPY%" -s %PHONE% --window-title "Pixel 9a - Ma Mangatheque" --stay-awake --video-bit-rate=8M --max-size=1600
