@echo off
REM Script de lancement de Gemstone Importer pour Windows

echo === Gemstone Importer ===
echo.

REM Vérifier si Maven est installé
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Erreur: Maven n'est pas installé
    echo Téléchargez Maven depuis: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

REM Vérifier si Java est installé
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Erreur: Java n'est pas installé
    echo Téléchargez Java 21 depuis: https://adoptium.net/
    pause
    exit /b 1
)

REM Créer les répertoires nécessaires
if not exist "data" mkdir data
if not exist "images" mkdir images
if not exist "logs" mkdir logs

REM Si le JAR n'existe pas, compiler
if not exist "target\gemstone-importer-1.0.0.jar" (
    echo Compilation de l'application...
    call mvn clean package
    if %ERRORLEVEL% NEQ 0 (
        echo Erreur lors de la compilation
        pause
        exit /b 1
    )
    echo.
)

REM Vérifier si le fichier de configuration existe
if not exist "config.properties" (
    echo Attention: config.properties n'existe pas
    echo Copie de config.properties.example...
    copy config.properties.example config.properties
    echo Veuillez modifier config.properties avec vos parametres PostgreSQL
    echo.
    pause
)

REM Lancer l'application
echo Demarrage de l'application...
echo.
java -jar target\gemstone-importer-1.0.0.jar

echo.
echo === Termine ===
pause
