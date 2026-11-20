#!/bin/bash
# Compila el proyecto (Java 17 o superior, sin frameworks).
cd "$(dirname "$0")/.." || exit 1
rm -rf out
find src -name '*.java' > fuentes.txt
javac --release 17 -encoding UTF-8 -cp lib/postgresql-42.7.4.jar -d out @fuentes.txt
RESULTADO=$?
rm -f fuentes.txt
if [ $RESULTADO -ne 0 ]; then
  echo "Error de compilacion."
  exit $RESULTADO
fi
echo "Compilacion correcta."
