#!/bin/bash

# Script de lancement de Gemstone Importer

echo "=== Gemstone Importer ==="
echo ""

# Vérifier si Maven est installé
if ! command -v mvn &> /dev/null; then
    echo "Erreur: Maven n'est pas installé"
    echo "Installez Maven avec: sudo apt install maven (Ubuntu/Debian)"
    exit 1
fi

# Vérifier si Java 21+ est installé
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "Erreur: Java 21 ou supérieur est requis"
    echo "Version actuelle: $JAVA_VERSION"
    exit 1
fi

# Créer les répertoires nécessaires
mkdir -p data images logs

# Si le JAR n'existe pas, compiler
if [ ! -f "target/gemstone-importer-1.0.0.jar" ]; then
    echo "Compilation de l'application..."
    mvn clean package
    if [ $? -ne 0 ]; then
        echo "Erreur lors de la compilation"
        exit 1
    fi
    echo ""
fi

# Vérifier si le fichier de configuration existe
if [ ! -f "config.properties" ]; then
    echo "Attention: config.properties n'existe pas"
    echo "Copie de config.properties.example..."
    cp config.properties.example config.properties
    echo "Veuillez modifier config.properties avec vos paramètres PostgreSQL"
    echo ""
    read -p "Appuyez sur Entrée pour continuer..."
fi

# Lancer l'application
echo "Démarrage de l'application..."
echo ""
java -jar target/gemstone-importer-1.0.0.jar

echo ""
echo "=== Terminé ==="
