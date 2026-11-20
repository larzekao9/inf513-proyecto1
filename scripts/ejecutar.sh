#!/bin/bash
# Ejecuta el sistema contra el servidor del curso (base db_grupo12sa y buzon grupo12sa@tecnoweb.org.bo), con config.properties.
#   ./scripts/ejecutar.sh                                   arranca el sistema: revisa el buzon y responde
#   ./scripts/ejecutar.sh --instalar                        crea o actualiza las tablas
#   ./scripts/ejecutar.sh --probar "help" correo@remitente  procesa un asunto sin correo (los cambios quedan en la base)
#   ./scripts/ejecutar.sh --buzon                           lista los correos del buzon sin borrarlos
# Compilar antes: ./scripts/compilar.sh
cd "$(dirname "$0")/.." || exit 1

# "real" se acepta por costumbre (antes habia un perfil local); no cambia nada
[ "$1" = "real" ] && shift

if [ ! -f config.properties ]; then
  echo "Falta config.properties: copie config.properties.example como config.properties y complete las contrasenas."
  exit 1
fi
java -Dcanal11.config=config.properties -cp "out:lib/postgresql-42.7.4.jar" canal11.App "$@"
