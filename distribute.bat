@echo off
echo Building WARNO Mod Maker Redistributable Package...
echo.

rem Step 1: Clean previous builds
echo Cleaning previous builds...
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
if exist temp_input rmdir /s /q temp_input
if exist WARNO-Mod-Maker rmdir /s /q WARNO-Mod-Maker
if exist WarnoModMaker.jar del WarnoModMaker.jar
if exist WARNO-Mod-Maker.zip del WARNO-Mod-Maker.zip

rem Step 2: Build the JAR file
echo.
echo Building JAR file...
call build.bat

if %ERRORLEVEL% neq 0 (
    echo Build failed!
    exit /b %ERRORLEVEL%
)

rem Step 3: Create redistributable package with jpackage using IntelliJ's Java 24
echo.
echo Creating redistributable package with jpackage...

rem Set JAVA_HOME to IntelliJ's Java 24 installation
set "JAVA_HOME=%USERPROFILE%\.jdks\openjdk-24.0.1"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Using Java from: %JAVA_HOME%

rem Create a clean input directory with only the JAR
if not exist temp_input mkdir temp_input
copy WarnoModMaker.jar temp_input\

"%JAVA_HOME%\bin\jpackage" ^
    --input temp_input ^
    --name "WARNO-Mod-Maker" ^
    --main-jar WarnoModMaker.jar ^
    --main-class com.warnomodmaker.WarnoModMaker ^
    --type app-image ^
    --dest . ^
    --app-version 1.0 ^
    --vendor "WARNO Mod Maker" ^
    --description "WARNO Mod Maker - NDF File Editor for WARNO Game Modifications" ^
    --copyright "2025" ^
    --java-options "-Xmx2g"

if %ERRORLEVEL% neq 0 (
    echo jpackage failed!
    rmdir /s /q temp_input
    exit /b %ERRORLEVEL%
)

rem Clean up temporary input directory
rmdir /s /q temp_input

rem Step 4: Clean up any unwanted directories in the package
echo.
echo Cleaning package structure...
if exist WARNO-Mod-Maker\resources rmdir /s /q WARNO-Mod-Maker\resources

rem Step 5: Create ZIP distribution
echo.
echo Creating ZIP distribution...
powershell -Command "Compress-Archive -Path 'WARNO-Mod-Maker' -DestinationPath 'WARNO-Mod-Maker.zip' -Force"

if %ERRORLEVEL% neq 0 (
    echo ZIP creation failed!
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================
echo Distribution package created successfully!
echo ========================================
echo.
echo Files created:
echo   - WARNO-Mod-Maker\          (Standalone application directory)
echo   - WARNO-Mod-Maker.exe       (Main executable)
echo   - WARNO-Mod-Maker.zip       (Complete distribution package)
echo.
echo To distribute:
echo   1. Share the WARNO-Mod-Maker.zip file
echo   2. Users extract and run WARNO-Mod-Maker.exe
echo   3. No Java installation required on user machines
echo.
echo Distribution complete!
