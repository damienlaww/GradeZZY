@echo off
rem ----------------------------------------------------------------------
rem clean.bat: empties classes\ and removes files created while grading.
rem Run this before zipping the project for submission: the brief requires
rem the classes folder to be empty.
rem ----------------------------------------------------------------------
setlocal
cd /d "%~dp0"

if exist classes rmdir /s /q classes
if exist workspace rmdir /s /q workspace
mkdir classes

rem A file still in use, such as workspace\gradezzy.db while GradeZZY is
rem open, is locked on Windows, so rmdir can leave part of it behind.
if exist workspace (
    echo workspace\ could not be removed completely. Close GradeZZY and run clean.bat again.
    exit /b 1
)

echo classes\ is empty and workspace\ is removed.
