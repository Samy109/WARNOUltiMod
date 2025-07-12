@echo off
echo Building WARNO Mod Maker Redistributable Package...
echo.

rem ========================================
rem CUSTOM JAVA CONFIGURATION
rem ========================================
rem Set your custom Java paths here (leave empty to use system Java)
rem CUSTOM_BUILD_JAVA: Java for compilation (should match build.bat)
rem CUSTOM_JPACKAGE_JAVA: Java for jpackage (needs Java 14+ with jpackage)
rem Examples:
rem   set CUSTOM_BUILD_JAVA=%USERPROFILE%\.jdks\openjdk-24.0.1
rem   set CUSTOM_JPACKAGE_JAVA=%USERPROFILE%\.jdks\openjdk-24.0.1
set CUSTOM_BUILD_JAVA=
set CUSTOM_JPACKAGE_JAVA=%USERPROFILE%\.jdks\openjdk-24.0.1

rem Step 1: Clean previous builds
echo Cleaning previous builds...
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
if exist temp_input rmdir /s /q temp_input
if exist WARNO-Mod-Maker rmdir /s /q WARNO-Mod-Maker
if exist WarnoModMaker.jar del WarnoModMaker.jar
if exist WARNO-Mod-Maker.zip del WARNO-Mod-Maker.zip

rem Step 2: Update build.bat with custom Java if specified
echo.
echo Preparing build configuration...
if defined CUSTOM_BUILD_JAVA (
    echo Configuring build.bat to use custom Java: %CUSTOM_BUILD_JAVA%
    rem Create a temporary build.bat with the custom Java path
    powershell -Command "(Get-Content build.bat) -replace 'set CUSTOM_JAVA_HOME=', 'set CUSTOM_JAVA_HOME=%CUSTOM_BUILD_JAVA%' | Set-Content build_temp.bat"
    call build_temp.bat
    del build_temp.bat
) else (
    echo Using build.bat with default Java configuration
    call build.bat
)

if %ERRORLEVEL% neq 0 (
    echo Build failed!
    exit /b %ERRORLEVEL%
)

rem Step 3: Rename fat JAR to expected name
echo.
echo Renaming fat JAR for distribution...
if exist WarnoModMaker.jar del WarnoModMaker.jar
rename WarnoModMaker-fat.jar WarnoModMaker.jar

rem Step 4: Create redistributable package with jpackage
echo.
echo Creating redistributable package with jpackage...

rem Setup jpackage Java
if defined CUSTOM_JPACKAGE_JAVA (
    echo Using custom jpackage Java from: %CUSTOM_JPACKAGE_JAVA%
    set "JPACKAGE_JAVA_HOME=%CUSTOM_JPACKAGE_JAVA%"
) else (
    echo Using system Java for jpackage
    set "JPACKAGE_JAVA_HOME=%JAVA_HOME%"
)

rem Verify jpackage Java exists
if not exist "%JPACKAGE_JAVA_HOME%\bin\jpackage.exe" (
    echo ERROR: jpackage not found at %JPACKAGE_JAVA_HOME%\bin\jpackage.exe
    echo Please ensure CUSTOM_JPACKAGE_JAVA points to a Java 14+ installation with jpackage
    exit /b 1
)

set "PATH=%JPACKAGE_JAVA_HOME%\bin;%PATH%"
echo Using jpackage from: %JPACKAGE_JAVA_HOME%

rem Create a clean input directory with only the JAR
if not exist temp_input mkdir temp_input
copy WarnoModMaker.jar temp_input\

"%JPACKAGE_JAVA_HOME%\bin\jpackage" ^
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
    --java-options "-Xmx4g"

if %ERRORLEVEL% neq 0 (
    echo jpackage failed!
    rmdir /s /q temp_input
    exit /b %ERRORLEVEL%
)

rem Clean up temporary input directory
rmdir /s /q temp_input

rem Step 5: Clean up any unwanted directories in the package
echo.
echo Cleaning package structure...
if exist WARNO-Mod-Maker\resources rmdir /s /q WARNO-Mod-Maker\resources

rem Step 6: Create ZIP distribution
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
