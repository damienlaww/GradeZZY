@echo off
rem ----------------------------------------------------------------------
rem run.bat: runs GradeZZY. Run compile.bat first.
rem
rem Usage (Windows Command Prompt):
rem   run.bat               open the window
rem   run.bat --check       load every screen once, then exit (no window)
rem   run.bat console       grade from the terminal, without JavaFX
rem   run.bat console G2    the same, for section G2
rem   run.bat console CS102 G2   the same, for module CS102, section G2
rem ----------------------------------------------------------------------
setlocal
cd /d "%~dp0"

set JAVAFX_DIR=lib\javafx\win
set CLASSPATH_VALUE=classes;lib\*

if not exist classes\gradezzy goto not_compiled
if /i "%~1"=="console" goto run_console

rem --module-path / --add-modules: JavaFX is not part of the JDK.
rem --enable-native-access: JavaFX and the SQLite driver load native code.
rem --add-exports / --add-opens: the complete list ControlsFX documents for
rem   its controls (AutoCompletionBinding, BreadCrumbBar, NotificationPane,
rem   GlyphFont, GridView, FilterPanel, TableRowExpanderColumn). ControlsFX
rem   is on the classpath, so the target is ALL-UNNAMED.
set FX_FLAGS=--module-path "%JAVAFX_DIR%" --add-modules javafx.controls,javafx.fxml
set FX_FLAGS=%FX_FLAGS% --enable-native-access=javafx.graphics,ALL-UNNAMED
set FX_FLAGS=%FX_FLAGS% --add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED
set FX_FLAGS=%FX_FLAGS% --add-exports=javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED
set FX_FLAGS=%FX_FLAGS% --add-exports=javafx.graphics/com.sun.javafx.scene.traversal=ALL-UNNAMED
set FX_FLAGS=%FX_FLAGS% --add-exports=javafx.graphics/com.sun.javafx.css=ALL-UNNAMED
set FX_FLAGS=%FX_FLAGS% --add-opens=javafx.controls/javafx.scene.control.skin=ALL-UNNAMED

java %FX_FLAGS% -cp "%CLASSPATH_VALUE%" gradezzy.ui.MainApp %*
exit /b %errorlevel%

:run_console
rem Pass on every argument after the word console.
set CONSOLE_ARGS=
shift
:next_argument
if "%~1"=="" goto start_console
set CONSOLE_ARGS=%CONSOLE_ARGS% %1
shift
goto next_argument

:start_console
rem --enable-native-access: the SQLite driver loads a native library.
java -cp "%CLASSPATH_VALUE%" --enable-native-access=ALL-UNNAMED gradezzy.app.ConsoleMain %CONSOLE_ARGS%
exit /b %errorlevel%

:not_compiled
echo Nothing is compiled yet. Run: compile.bat
exit /b 1
