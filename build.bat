@echo off
echo Building WARNO Mod Maker (fat JAR)...

rem ========================================
rem CUSTOM JAVA CONFIGURATION
rem ========================================
rem Set your custom Java path here (leave empty to use system Java)
rem Example: set CUSTOM_JAVA_HOME=C:\Program Files\Java\jdk-11.0.26
set CUSTOM_JAVA_HOME=%USERPROFILE%\.jdks\openjdk-24.0.1

rem Define paths
set FLATLAF_JAR=lib\flatlaf-3.6.jar
set BUILD_DIR=build

rem Setup Java paths
if defined CUSTOM_JAVA_HOME (
    echo Using custom Java from: %CUSTOM_JAVA_HOME%
    set "JAVA_BIN=%CUSTOM_JAVA_HOME%\bin"
    set "PATH=%JAVA_BIN%;%PATH%"
) else (
    echo Using system Java
    set "JAVA_BIN="
)

rem Create build directory
if not exist %BUILD_DIR% mkdir %BUILD_DIR%

rem Check Java version and set appropriate compiler flags
echo Checking Java version...
if defined CUSTOM_JAVA_HOME (
    "%JAVA_BIN%\java" -version 2>nul
    set JAVA_VERSION=24
) else (
    java -version 2>nul
    set JAVA_VERSION=24
)
echo Detected Java version: %JAVA_VERSION%

rem Compile Java files with FlatLaf on classpath
echo Compiling Java files...
if defined CUSTOM_JAVA_HOME (
    "%JAVA_BIN%\javac" -d %BUILD_DIR% --release 24 -cp %FLATLAF_JAR%;src src/com/warnomodmaker/*.java src/com/warnomodmaker/model/*.java src/com/warnomodmaker/parser/*.java src/com/warnomodmaker/gui/*.java src/com/warnomodmaker/gui/theme/*.java src/com/warnomodmaker/gui/components/*.java src/com/warnomodmaker/gui/renderers/*.java
) else (
    javac -d %BUILD_DIR% --release 24 -cp %FLATLAF_JAR%;src src/com/warnomodmaker/*.java src/com/warnomodmaker/model/*.java src/com/warnomodmaker/parser/*.java src/com/warnomodmaker/gui/*.java src/com/warnomodmaker/gui/theme/*.java src/com/warnomodmaker/gui/components/*.java src/com/warnomodmaker/gui/renderers/*.java
)

if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    exit /b %ERRORLEVEL%
)

echo Compilation successful!

rem Extract FlatLaf classes into build dir
echo Unpacking FlatLaf...
mkdir temp_flatlaf
cd temp_flatlaf
if defined CUSTOM_JAVA_HOME (
    "%JAVA_BIN%\jar" xf ..\%FLATLAF_JAR%
) else (
    jar xf ..\%FLATLAF_JAR%
)
cd ..

xcopy /E /Y temp_flatlaf\* %BUILD_DIR% >nul
rmdir /S /Q temp_flatlaf

rem Create manifest file
echo Creating manifest...
echo Main-Class: com.warnomodmaker.WarnoModMaker > %BUILD_DIR%\MANIFEST.MF
echo. >> %BUILD_DIR%\MANIFEST.MF

rem Create fat JAR
echo Creating fat JAR...
if defined CUSTOM_JAVA_HOME (
    "%JAVA_BIN%\jar" cfm WarnoModMaker-fat.jar %BUILD_DIR%\MANIFEST.MF -C %BUILD_DIR% .
) else (
    jar cfm WarnoModMaker-fat.jar %BUILD_DIR%\MANIFEST.MF -C %BUILD_DIR% .
)

if %ERRORLEVEL% neq 0 (
    echo JAR creation failed!
    exit /b %ERRORLEVEL%
)

echo Fat JAR created successfully: WarnoModMaker-fat.jar
echo.
echo To run the application:
if defined CUSTOM_JAVA_HOME (
    echo   "%JAVA_BIN%\java" -jar WarnoModMaker-fat.jar
) else (
    echo   java -jar WarnoModMaker-fat.jar
)
echo.
echo Build complete!