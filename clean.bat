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

echo classes\ is empty and workspace\ is removed.
