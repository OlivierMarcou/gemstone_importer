# Gemstone Importer - Importateur de Pierres Précieuses

Application Java 21 pour importer des données de pierres précieuses depuis des fichiers Excel et HTML vers une base de données PostgreSQL.

## 📋 Fonctionnalités

- **Parsing de fichiers Excel** (.xlsx) contenant des données de pierres précieuses
- **Parsing de fichiers HTML** avec rapports d'achats eBay
- **Extraction automatique** des informations (type de pierre, poids en carats, dimensions, origine, etc.)
- **Base de données PostgreSQL** avec schéma relationnel complet
- **Détection de doublons** basée sur l'ID de l'article
- **Téléchargement automatique** des images des pierres
- **Statistiques** et vues pour analyser la collection
- **Fusion intelligente** des données provenant de sources multiples

## 🚀 Installation

### Prérequis

- **Java 21** ou supérieur
- **Maven 3.8+**
- **PostgreSQL 12+**

### Étapes d'installation

1. **Cloner ou décompresser le projet**
```bash
cd gemstone-importer
```

2. **Créer la base de données PostgreSQL**
```bash
psql -U postgres -f create_database.sql
```

Ou manuellement :
```sql
CREATE DATABASE gemstones;
```

3. **Configurer l'application**

Copiez le fichier de configuration d'exemple :
```bash
cp config.properties.example config.properties
```

Modifiez `config.properties` avec vos paramètres :
```properties
db.url=jdbc:postgresql://localhost:5432/gemstones
db.username=postgres
db.password=votre_mot_de_passe
db.create.schema=true
images.directory=images
import.excel.directory=data
import.html.directory=data
```

4. **Compiler l'application**
```bash
mvn clean package
```

## 📂 Structure du projet

```
gemstone-importer/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/gemstone/importer/
│       │       ├── GemstoneImporter.java      # Classe principale
│       │       ├── model/
│       │       │   └── Gemstone.java          # Modèle de données
│       │       ├── parser/
│       │       │   ├── ExcelParser.java       # Parser Excel
│       │       │   └── HtmlParser.java        # Parser HTML
│       │       └── database/
│       │           └── DatabaseManager.java   # Gestionnaire BDD
│       └── resources/
│           ├── default-config.properties      # Config par défaut
│           └── logback.xml                    # Config des logs
├── data/                                      # Vos fichiers Excel/HTML
├── images/                                    # Images téléchargées
├── logs/                                      # Fichiers de logs
├── pom.xml                                    # Configuration Maven
├── config.properties                          # Votre configuration
└── README.md                                  # Ce fichier
```

## 🎯 Utilisation

### Première exécution

1. **Placez vos fichiers** Excel et HTML dans le répertoire `data/` (ou le répertoire configuré)

2. **Assurez-vous** que `db.create.schema=true` dans `config.properties`

3. **Lancez l'application** :
```bash
java -jar target/gemstone-importer-1.0.0.jar
```

Ou avec Maven :
```bash
mvn exec:java -Dexec.mainClass="com.gemstone.importer.GemstoneImporter"
```

### Exécutions suivantes

1. **Changez** `db.create.schema=false` dans `config.properties` pour ne pas recréer le schéma

2. **Ajoutez de nouveaux fichiers** dans le répertoire `data/`

3. **Relancez l'application** - seules les nouvelles pierres seront ajoutées

## 📊 Structure de la base de données

### Table principale : `gemstones`

| Colonne | Type | Description |
|---------|------|-------------|
| id | SERIAL | Identifiant unique |
| article_id | VARCHAR(50) | ID de l'article (unique) |
| title | TEXT | Titre complet |
| description | TEXT | Description |
| price | DECIMAL(10,2) | Prix d'achat |
| quantity | INTEGER | Quantité |
| purchase_date | TIMESTAMP | Date d'achat |
| order_id | VARCHAR(50) | ID de commande |
| transaction_id | VARCHAR(50) | ID de transaction |
| listing_url | TEXT | URL de l'annonce |
| image_url | TEXT | URL de l'image originale |
| local_image_path | TEXT | Chemin local de l'image |
| **gem_type** | VARCHAR(50) | Type de pierre (Sapphire, Ruby, etc.) |
| **carats** | DECIMAL(10,2) | Poids en carats |
| **dimensions** | VARCHAR(50) | Dimensions (ex: 8.8x7.5mm) |
| **shape** | VARCHAR(50) | Forme (Heart, Oval, etc.) |
| **color** | VARCHAR(50) | Couleur |
| **clarity** | VARCHAR(20) | Clarté (VS, VVS, etc.) |
| **treatment** | VARCHAR(50) | Traitement (Heated, Unheated) |
| **origin** | VARCHAR(100) | Origine (Burma, Ceylon, etc.) |
| created_at | TIMESTAMP | Date de création |
| updated_at | TIMESTAMP | Date de mise à jour |

### Vues disponibles

#### `gemstone_statistics`
Statistiques globales sur la collection :
```sql
SELECT * FROM gemstone_statistics;
```
- Nombre total de pierres
- Valeur totale
- Prix moyen
- Total de carats
- Nombre de types distincts
- Nombre d'origines distinctes

#### `gemstones_by_type`
Statistiques par type de pierre :
```sql
SELECT * FROM gemstones_by_type;
```

#### `gemstones_by_origin`
Statistiques par origine :
```sql
SELECT * FROM gemstones_by_origin;
```

## 🔍 Exemples de requêtes SQL

### Trouver toutes les pierres d'un type spécifique
```sql
SELECT * FROM gemstones 
WHERE gem_type = 'Sapphire' 
ORDER BY carats DESC;
```

### Trouver les pierres les plus chères
```sql
SELECT title, gem_type, carats, price, origin 
FROM gemstones 
ORDER BY price DESC 
LIMIT 10;
```

### Calculer la valeur par origine
```sql
SELECT origin, COUNT(*) as count, SUM(price) as total_value
FROM gemstones 
WHERE origin IS NOT NULL
GROUP BY origin 
ORDER BY total_value DESC;
```

### Trouver les pierres non chauffées
```sql
SELECT * FROM gemstones 
WHERE treatment = 'Unheated' 
ORDER BY price DESC;
```

## 🛠️ Configuration avancée

### Changer le niveau de logs

Modifiez `src/main/resources/logback.xml` :
```xml
<root level="DEBUG">  <!-- INFO, DEBUG, WARN, ERROR -->
```

### Personnaliser les répertoires

Dans `config.properties` :
```properties
import.excel.directory=/chemin/vers/excel
import.html.directory=/chemin/vers/html
images.directory=/chemin/vers/images
```

### Désactiver le téléchargement d'images

Dans `DatabaseManager.java`, commentez l'appel à `downloadImage()`.

## 📝 Format des fichiers supportés

### Excel (.xlsx)
Colonnes attendues :
1. ID Article
2. Titre
3. Description
4. Prix
5. Quantité
6. Date d'achat
7. ID Commande
8. ID Transaction
9. État
10. URL Annonce
11. URL Image
12. Nb Images
13. Nb Vidéos

### HTML
Structure attendue :
```html
<div class="item-card">
    <div class="item-images">
        <img src="..." alt="...">
    </div>
    <h3 class="item-title">...</h3>
    <div class="item-price">...</div>
    <div class="item-date">...</div>
    <div class="item-detail">ID: ...</div>
    <div class="item-detail">Quantité: ...</div>
    <div class="item-detail">Commande: ...</div>
</div>
```

## 🐛 Dépannage

### Erreur de connexion PostgreSQL
- Vérifiez que PostgreSQL est démarré : `sudo service postgresql status`
- Vérifiez l'URL, le nom d'utilisateur et le mot de passe dans `config.properties`
- Vérifiez que la base de données existe : `psql -U postgres -l`

### Erreur "Table already exists"
- Changez `db.create.schema=false` dans `config.properties`

### Images non téléchargées
- Vérifiez votre connexion internet
- Vérifiez les permissions du répertoire `images/`
- Consultez les logs dans `logs/gemstone-importer.log`

### Fichiers non trouvés
- Vérifiez les chemins dans `config.properties`
- Vérifiez que les fichiers ont les bonnes extensions (.xlsx, .html)

## 📜 Licence

Ce projet est fourni tel quel, sans garantie d'aucune sorte.

## 🤝 Support

Pour toute question ou problème, consultez les logs dans `logs/gemstone-importer.log`.

## 🎨 Évolutions futures possibles

- Interface graphique (JavaFX)
- Export des données (Excel, CSV, PDF)
- Gestion des modifications de pierres
- Ajout de photos multiples
- Calcul automatique de la valeur d'assurance
- Historique des prix
- Comparaison avec les prix du marché
- Alertes pour les bonnes affaires
