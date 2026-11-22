@echo off
setlocal
cd /d "%~dp0" || exit /b 1
if defined CUSTOM_JAVA_HOME set "PATH=%CUSTOM_JAVA_HOME%\bin;%PATH%"
if not defined CUSTOM_JAVA_HOME if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

set "CLASSES=build\fat-classes"
if exist "%CLASSES%" rmdir /s /q "%CLASSES%"
mkdir "%CLASSES%" || exit /b 1
powershell -NoProfile -Command "$sources = Get-ChildItem src/com -Filter *.java -Recurse | ForEach-Object { [char]34 + $_.FullName.Replace([char]92,[char]47) + [char]34 }; [IO.File]::WriteAllLines('build/sources.txt', [string[]]$sources)" || exit /b 1
javac --release 25 -encoding UTF-8 -classpath "lib\*" -d "%CLASSES%" @build\sources.txt || exit /b 1

pushd "%CLASSES%" || exit /b 1
jar xf "..\..\lib\flatlaf-3.6.jar" || exit /b 1
jar xf "..\..\lib\aircompressor-0.27.jar" || exit /b 1
if exist module-info.class del module-info.class
for /r META-INF %%F in (module-info.class) do if exist "%%F" del "%%F"
popd
jar --create --file WarnoModMaker-fat.jar --main-class com.warnomodmaker.WarnoModMaker -C "%CLASSES%" . || exit /b 1
echo Built WarnoModMaker-fat.jar
