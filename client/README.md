# 💎 Gemstone Viewer

Application Java 21 Swing pour visualiser et rechercher votre collection de pierres précieuses depuis la base de données PostgreSQL créée par [gemstone_importer](https://github.com/OlivierMarcou/gemstone_importer).

## 🚀 Lancement rapide (JAR inclus)

Le fichier `gemstone-viewer.jar` est prêt à l'emploi et inclut le driver PostgreSQL.

### Linux / macOS
```bash
chmod +x run.sh
./run.sh
```

### Windows
```
run.bat
```

### Direct
```bash
java -jar gemstone-viewer.jar
```

## 🎨 Thème amélioré avec FlatLaf (recommandé)

Le JAR fourni utilise le thème Nimbus intégré. Pour un rendu encore plus moderne, compilez avec Maven (inclut FlatLaf) :

```bash
mvn clean package
java -jar target/gemstone-viewer.jar
```

## ✨ Fonctionnalités

- **Connexion PostgreSQL** avec sauvegarde des paramètres
- **Recherche multi-critères** : type, couleur, forme, clarté, traitement, origine, carats, prix
- **Tableau de résultats** avec tri par colonnes
- **Fiche détaillée** avec image, tous les champs, lien eBay
- **Statistiques** globales dans l'en-tête (total, valeur, carats, types, origines)
- **Interface sombre** moderne

## 📋 Prérequis

- Java 21+
- Base de données PostgreSQL créée par gemstone_importer
