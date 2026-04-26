@echo off
setlocal

echo Building WARNO Mod Maker (fat JAR)...

rem Optional overrides before running this script:
rem   set CUSTOM_JAVA_HOME=C:\Program Files\Java\jdk-17
rem   set BUILD_JAVA_RELEASE=17
if not defined CUSTOM_JAVA_HOME set "CUSTOM_JAVA_HOME="
if not defined BUILD_JAVA_RELEASE set "BUILD_JAVA_RELEASE=17"

set "FLATLAF_JAR=lib\flatlaf-3.6.jar"
set "BUILD_DIR=build"
set "TEMP_FLATLAF_DIR=temp_flatlaf"
set "OUTPUT_JAR=WarnoModMaker-fat.jar"

if not exist "%FLATLAF_JAR%" (
    echo Missing dependency: %FLATLAF_JAR%
    exit /b 1
)

if defined CUSTOM_JAVA_HOME (
    if not exist "%CUSTOM_JAVA_HOME%\bin\java.exe" (
        echo CUSTOM_JAVA_HOME does not contain java.exe: %CUSTOM_JAVA_HOME%
        exit /b 1
    )
    if not exist "%CUSTOM_JAVA_HOME%\bin\javac.exe" (
        echo CUSTOM_JAVA_HOME does not contain javac.exe: %CUSTOM_JAVA_HOME%
        exit /b 1
    )
    if not exist "%CUSTOM_JAVA_HOME%\bin\jar.exe" (
        echo CUSTOM_JAVA_HOME does not contain jar.exe: %CUSTOM_JAVA_HOME%
        exit /b 1
    )

    set "JAVA_CMD=%CUSTOM_JAVA_HOME%\bin\java.exe"
    set "JAVAC_CMD=%CUSTOM_JAVA_HOME%\bin\javac.exe"
    set "JAR_CMD=%CUSTOM_JAVA_HOME%\bin\jar.exe"
    echo Using CUSTOM_JAVA_HOME: %CUSTOM_JAVA_HOME%
) else (
    where java >nul 2>nul || (
        echo Java was not found on PATH.
        exit /b 1
    )
    where javac >nul 2>nul || (
        echo javac was not found on PATH.
        exit /b 1
    )
    where jar >nul 2>nul || (
        echo jar was not found on PATH.
        exit /b 1
    )

    set "JAVA_CMD=java"
    set "JAVAC_CMD=javac"
    set "JAR_CMD=jar"
    echo Using Java from PATH
)

echo Checking Java version...
"%JAVA_CMD%" -version
if %ERRORLEVEL% neq 0 (
    echo Failed to run Java.
    exit /b %ERRORLEVEL%
)

for /f "tokens=2 delims= " %%v in ('"%JAVAC_CMD%" -version 2^>^&1') do set "JAVAC_VERSION=%%v"
set "JAVAC_MAJOR=%JAVAC_VERSION%"
if "%JAVAC_VERSION:~0,2%"=="1." set "JAVAC_MAJOR=%JAVAC_VERSION:~2,1%"
for /f "tokens=1 delims=." %%m in ("%JAVAC_MAJOR%") do set "JAVAC_MAJOR=%%m"

if %JAVAC_MAJOR% LSS %BUILD_JAVA_RELEASE% (
    echo Java %BUILD_JAVA_RELEASE% or newer is required to build this project.
    echo Detected javac %JAVAC_VERSION%.
    echo Set CUSTOM_JAVA_HOME to a newer JDK or lower BUILD_JAVA_RELEASE only if you have removed newer language features.
    exit /b 1
)

echo Preparing build directory...
if exist "%BUILD_DIR%" rmdir /S /Q "%BUILD_DIR%"
mkdir "%BUILD_DIR%"

echo Compiling Java files with --release %BUILD_JAVA_RELEASE%...
"%JAVAC_CMD%" -d "%BUILD_DIR%" --release %BUILD_JAVA_RELEASE% -cp "%FLATLAF_JAR%;src" src/com/warnomodmaker/*.java src/com/warnomodmaker/model/*.java src/com/warnomodmaker/parser/*.java src/com/warnomodmaker/gui/*.java src/com/warnomodmaker/gui/theme/*.java src/com/warnomodmaker/gui/components/*.java src/com/warnomodmaker/gui/renderers/*.java
if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    exit /b %ERRORLEVEL%
)

echo Unpacking FlatLaf...
if exist "%TEMP_FLATLAF_DIR%" rmdir /S /Q "%TEMP_FLATLAF_DIR%"
mkdir "%TEMP_FLATLAF_DIR%"
pushd "%TEMP_FLATLAF_DIR%"
"%JAR_CMD%" xf "..\%FLATLAF_JAR%"
if %ERRORLEVEL% neq 0 (
    popd
    echo Failed to unpack FlatLaf.
    exit /b %ERRORLEVEL%
)
popd

xcopy /E /I /Y "%TEMP_FLATLAF_DIR%\*" "%BUILD_DIR%" >nul
rmdir /S /Q "%TEMP_FLATLAF_DIR%"

echo Creating manifest...
echo Main-Class: com.warnomodmaker.WarnoModMaker > "%BUILD_DIR%\MANIFEST.MF"
echo. >> "%BUILD_DIR%\MANIFEST.MF"

echo Creating fat JAR...
"%JAR_CMD%" cfm "%OUTPUT_JAR%" "%BUILD_DIR%\MANIFEST.MF" -C "%BUILD_DIR%" .
if %ERRORLEVEL% neq 0 (
    echo JAR creation failed!
    exit /b %ERRORLEVEL%
)

echo Fat JAR created successfully: %OUTPUT_JAR%
echo.
echo To run the application:
echo   "%JAVA_CMD%" -jar "%OUTPUT_JAR%"
echo.
echo Build complete!
endlocal