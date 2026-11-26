@echo off
setlocal
cd /d "%~dp0" || exit /b 1
if defined CUSTOM_JAVA_HOME set "PATH=%CUSTOM_JAVA_HOME%\bin;%PATH%"
if not defined CUSTOM_JAVA_HOME if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
for /f "tokens=1 delims=." %%V in ('jpackage --version') do set "PACKAGER_VERSION=%%V"
if not "%PACKAGER_VERSION%"=="25" (
    echo Set CUSTOM_JAVA_HOME to a JDK 25 installation to package the runtime.
    exit /b 1
)
call build.bat || exit /b 1
set "STAGE=build\distribution"
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%\input" || exit /b 1
copy /y WarnoModMaker-fat.jar "%STAGE%\input\WarnoModMaker.jar" >nul || exit /b 1
jpackage --type app-image --dest "%STAGE%" --input "%STAGE%\input" ^
    --name "WARNO-Mod-Maker" --main-jar WarnoModMaker.jar ^
    --app-version 3.5.0 --vendor "WARNO Mod Maker" ^
    --description "WARNO NDF editor, mod combiner and map tool" ^
    --java-options "-Xmx6192m" --java-options "--sun-misc-unsafe-memory-access=allow" || exit /b 1
jar --create --no-manifest --file WARNO-Mod-Maker.zip -C "%STAGE%" WARNO-Mod-Maker || exit /b 1
echo Built WARNO-Mod-Maker.zip
