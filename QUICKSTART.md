# Guide de Démarrage Rapide - Gemstone Importer

## Installation en 5 minutes

### 1. Prérequis
- Java 21 installé : `java -version`
- Maven installé : `mvn -version`
- PostgreSQL installé et démarré

### 2. Créer la base de données
```bash
sudo -u postgres psql
CREATE DATABASE gemstones;
\q
```

### 3. Configurer l'application
```bash
cp config.properties.example config.properties
nano config.properties  # ou notepad config.properties sur Windows
```

Modifiez :
```properties
db.password=rootdest
```

### 4. Préparer vos fichiers
```bash
mkdir data
# Copiez vos fichiers .xlsx et .html dans le répertoire data/
```

### 5. Lancer l'application

**Linux/Mac :**
```bash
./run.sh
```

**Windows :**
```cmd
run.bat
```

**Ou manuellement :**
```bash
mvn clean package
java -jar target/gemstone-importer-1.0.0.jar
```

## Première exécution

L'application va :
1. ✅ Créer le schéma de la base de données
2. ✅ Parser vos fichiers Excel et HTML
3. ✅ Extraire les informations des pierres
4. ✅ Télécharger les images
5. ✅ Insérer les données dans PostgreSQL
6. ✅ Afficher les statistiques

## Exécutions suivantes

1. Modifiez `config.properties` :
```properties
db.create.schema=false
```

2. Ajoutez de nouveaux fichiers dans `data/`

3. Relancez : `./run.sh` ou `run.bat`

Seules les nouvelles pierres seront ajoutées (pas de doublons).

## Vérifier les données

```bash
psql -U postgres -d gemstones
```

```sql
-- Compter les pierres
SELECT COUNT(*) FROM gemstones;

-- Voir les statistiques
SELECT * FROM gemstone_statistics;

-- Voir les 10 premières pierres
SELECT article_id, title, price, gem_type, origin 
FROM gemstones 
LIMIT 10;
```

## Problèmes courants

### PostgreSQL refuse la connexion
```bash
sudo service postgresql start    # Linux
brew services start postgresql   # Mac
```

### Erreur "password authentication failed"
Vérifiez votre mot de passe dans `config.properties`

### Fichiers non trouvés
Vérifiez que vos fichiers sont bien dans le répertoire `data/`

### Images non téléchargées
Vérifiez votre connexion internet et les permissions du répertoire `images/`

## Consulter les logs

```bash
tail -f logs/gemstone-importer.log
```

## Support

Consultez le fichier README.md complet pour plus d'informations.
