@echo off
rem Compila el proyecto (Java 17 o superior, sin frameworks).
cd /d "%~dp0.."
if exist out rmdir /s /q out
dir /s /b src\*.java > fuentes.txt
javac --release 17 -encoding UTF-8 -cp lib\postgresql-42.7.4.jar -d out @fuentes.txt
set RESULTADO=%ERRORLEVEL%
del fuentes.txt
if %RESULTADO% neq 0 (
    echo Error de compilacion.
    exit /b %RESULTADO%
)
echo Compilacion correcta.
