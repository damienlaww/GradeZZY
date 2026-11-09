@echo off
rem ----------------------------------------------------------------------
rem compile.bat: compiles every Java source file in src\ into classes\.
rem
rem Usage (Windows Command Prompt):
rem   compile.bat         compile the whole application
rem   compile.bat core    compile only the grading core, without JavaFX
rem
rem javac does not copy resource files, so this script also copies the
rem .css, font and logo files of the ui package into classes\.
rem
rem When you add a new package, add its folder to CORE_SOURCES below and
rem to compile.sh.
rem ----------------------------------------------------------------------
setlocal
cd /d "%~dp0"

set JAVAFX_DIR=lib\javafx\win

rem Every package except ui. None of these may import javafx.
set CORE_SOURCES=src\gradezzy\app\*.java src\gradezzy\config\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\detection\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\exec\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\fix\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\grading\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\ingest\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\model\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\persistence\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\plagiarism\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\report\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\service\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\stats\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\testcase\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\util\*.java
set CORE_SOURCES=%CORE_SOURCES% src\gradezzy\validation\*.java
set UI_SOURCES=src\gradezzy\ui\*.java src\gradezzy\ui\backend\*.java
set UI_SOURCES=%UI_SOURCES% src\gradezzy\ui\check\*.java
set UI_SOURCES=%UI_SOURCES% src\gradezzy\ui\demo\*.java
set UI_SOURCES=%UI_SOURCES% src\gradezzy\ui\flow\*.java

rem Start from an empty classes folder so no old class file is left behind.
if exist classes rmdir /s /q classes
mkdir classes

if /i "%~1"=="core" goto compile_core

rem A line that ends with ^ continues on the next line.
javac --release 25 -encoding UTF-8 -d classes --module-path "%JAVAFX_DIR%" ^
    --add-modules javafx.controls,javafx.fxml -cp "lib\*" ^
    %CORE_SOURCES% %UI_SOURCES%
if errorlevel 1 goto failed

rem Copy the resources javac leaves behind.
mkdir classes\gradezzy\ui\css
copy /y src\gradezzy\ui\css\*.css classes\gradezzy\ui\css\ >nul
if errorlevel 1 goto failed
mkdir classes\gradezzy\ui\fonts
copy /y src\gradezzy\ui\fonts\*.ttf classes\gradezzy\ui\fonts\ >nul
if errorlevel 1 goto failed
copy /y src\gradezzy\ui\fonts\OFL.txt classes\gradezzy\ui\fonts\ >nul
if errorlevel 1 goto failed
mkdir classes\gradezzy\ui\images
copy /y src\gradezzy\ui\images\*.png classes\gradezzy\ui\images\ >nul
if errorlevel 1 goto failed

echo Compiled GradeZZY into classes\ using %JAVAFX_DIR%.
exit /b 0

:compile_core
rem No JavaFX and no jar on the classpath: proves the core stands alone.
javac --release 25 -encoding UTF-8 -d classes %CORE_SOURCES%
if errorlevel 1 goto failed
echo Compiled the grading core into classes\ (no JavaFX).
exit /b 0

:failed
echo Compilation failed. See the messages above.
exit /b 1
