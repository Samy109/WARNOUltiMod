@echo off
echo Building WARNO Mod Maker...

rem Create build directory
if not exist build mkdir build

rem Compile all Java files recursively
echo Compiling Java files...
javac -d build -source 11 -target 11 -cp src src/com/warnomodmaker/*.java src/com/warnomodmaker/model/*.java src/com/warnomodmaker/parser/*.java src/com/warnomodmaker/gui/*.java

if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    exit /b %ERRORLEVEL%
)

echo Compilation successful!

rem Create manifest file
echo Creating manifest...
echo Main-Class: com.warnomodmaker.WarnoModMaker > build/MANIFEST.MF
echo. >> build/MANIFEST.MF

rem Create JAR file
echo Creating JAR file...
jar cfm WarnoModMaker.jar build/MANIFEST.MF -C build .

if %ERRORLEVEL% neq 0 (
    echo JAR creation failed!
    exit /b %ERRORLEVEL%
)

echo JAR file created successfully: WarnoModMaker.jar
echo.
echo To run the application:
echo   java -jar WarnoModMaker.jar
echo.
echo Build complete!
