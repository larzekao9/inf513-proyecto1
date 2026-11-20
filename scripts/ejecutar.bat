@echo off
rem Ejecuta el sistema contra el servidor del curso (base db_grupo12sa y buzon grupo12sa@tecnoweb.org.bo), con config.properties.
rem   scripts\ejecutar.bat                                   arranca el sistema: revisa el buzon y responde
rem   scripts\ejecutar.bat --instalar                        crea o actualiza las tablas
rem   scripts\ejecutar.bat --probar "help" correo@remitente  procesa un asunto sin correo (los cambios quedan en la base)
rem   scripts\ejecutar.bat --buzon                           lista los correos del buzon sin borrarlos
rem Compilar antes: scripts\compilar.bat
cd /d "%~dp0.."
rem "real" se acepta por costumbre (antes habia un perfil local); no cambia nada
if /i "%1"=="real" shift
if not exist config.properties (
    echo Falta config.properties: copie config.properties.example como config.properties y complete las contrasenas.
    exit /b 1
)
java -Dcanal11.config=config.properties -cp "out;lib\postgresql-42.7.4.jar" canal11.App %1 %2 %3 %4 %5 %6 %7 %8 %9
