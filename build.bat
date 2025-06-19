@echo off
echo Building WARNO Mod Maker (fat JAR)...

rem Define paths
set FLATLAF_JAR=lib\flatlaf-3.6.jar
set BUILD_DIR=build

rem Create build directory
if not exist %BUILD_DIR% mkdir %BUILD_DIR%

rem Check Java version and set appropriate compiler flags
echo Checking Java version...
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION=%%g
)
set JAVA_VERSION=%JAVA_VERSION:"=%
echo Detected Java version: %JAVA_VERSION%

rem Compile Java files with FlatLaf on classpath
echo Compiling Java files...
javac -d %BUILD_DIR% -source 11 -target 11 -cp %FLATLAF_JAR%;src src/com/warnomodmaker/*.java src/com/warnomodmaker/model/*.java src/com/warnomodmaker/parser/*.java src/com/warnomodmaker/gui/*.java src/com/warnomodmaker/gui/theme/*.java src/com/warnomodmaker/gui/components/*.java src/com/warnomodmaker/gui/renderers/*.java

if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    exit /b %ERRORLEVEL%
)

echo Compilation successful!

rem Extract FlatLaf classes into build dir
echo Unpacking FlatLaf...
mkdir temp_flatlaf
cd temp_flatlaf
jar xf ..\%FLATLAF_JAR%
cd ..

xcopy /E /Y temp_flatlaf\* %BUILD_DIR% >nul
rmdir /S /Q temp_flatlaf

rem Create manifest file
echo Creating manifest...
echo Main-Class: com.warnomodmaker.WarnoModMaker > %BUILD_DIR%\MANIFEST.MF
echo. >> %BUILD_DIR%\MANIFEST.MF

rem Create fat JAR
echo Creating fat JAR...
jar cfm WarnoModMaker-fat.jar %BUILD_DIR%\MANIFEST.MF -C %BUILD_DIR% .

if %ERRORLEVEL% neq 0 (
    echo JAR creation failed!
    exit /b %ERRORLEVEL%
)

echo Fat JAR created successfully: WarnoModMaker-fat.jar
echo.
echo To run the application:
echo   java -jar WarnoModMaker-fat.jar
echo.
echo Build complete!