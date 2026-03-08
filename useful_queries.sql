-- ============================================
-- REQUÊTES SQL UTILES - GEMSTONE DATABASE
-- ============================================

-- ============================================
-- STATISTIQUES GÉNÉRALES
-- ============================================

-- Vue d'ensemble de la collection
SELECT * FROM gemstone_statistics;

-- Statistiques par type de pierre
SELECT * FROM gemstones_by_type;

-- Statistiques par origine
SELECT * FROM gemstones_by_origin;

-- Nombre total de pierres et valeur totale
SELECT 
    COUNT(*) as total_pierres,
    SUM(price) as valeur_totale,
    AVG(price) as prix_moyen,
    SUM(carats) as total_carats
FROM gemstones;


-- ============================================
-- RECHERCHES PAR TYPE
-- ============================================

-- Tous les saphirs
SELECT * FROM gemstones 
WHERE gem_type = 'Sapphire' 
ORDER BY carats DESC;

-- Tous les rubis
SELECT * FROM gemstones 
WHERE gem_type = 'Ruby' 
ORDER BY price DESC;

-- Toutes les émeraudes
SELECT * FROM gemstones 
WHERE gem_type = 'Emerald' 
ORDER BY price DESC;

-- Types les plus représentés
SELECT gem_type, COUNT(*) as nombre
FROM gemstones 
WHERE gem_type IS NOT NULL
GROUP BY gem_type
ORDER BY nombre DESC;


-- ============================================
-- RECHERCHES PAR ORIGINE
-- ============================================

-- Pierres de Birmanie/Myanmar
SELECT * FROM gemstones 
WHERE origin IN ('Burma', 'Myanmar')
ORDER BY price DESC;

-- Pierres de Madagascar
SELECT * FROM gemstones 
WHERE origin = 'Madagascar'
ORDER BY carats DESC;

-- Pierres du Sri Lanka/Ceylon
SELECT * FROM gemstones 
WHERE origin IN ('Ceylon', 'Sri Lanka')
ORDER BY price DESC;

-- Origines les plus fréquentes
SELECT origin, COUNT(*) as nombre, SUM(price) as valeur_totale
FROM gemstones 
WHERE origin IS NOT NULL
GROUP BY origin
ORDER BY nombre DESC;


-- ============================================
-- RECHERCHES PAR CARACTÉRISTIQUES
-- ============================================

-- Pierres non chauffées (Unheated)
SELECT * FROM gemstones 
WHERE treatment = 'Unheated'
ORDER BY price DESC;

-- Pierres de haute clarté (VS ou mieux)
SELECT * FROM gemstones 
WHERE clarity IN ('FL', 'IF', 'VVS1', 'VVS2', 'VVS', 'VS1', 'VS2', 'VS')
ORDER BY price DESC;

-- Pierres de grande taille (plus de 2 carats)
SELECT * FROM gemstones 
WHERE carats >= 2.0
ORDER BY carats DESC;

-- Pierres en forme de coeur
SELECT * FROM gemstones 
WHERE shape = 'Heart'
ORDER BY price DESC;


-- ============================================
-- RECHERCHES PAR PRIX
-- ============================================

-- Top 20 des pierres les plus chères
SELECT article_id, title, gem_type, carats, price, origin
FROM gemstones 
ORDER BY price DESC 
LIMIT 20;

-- Pierres bon marché (moins de 5€)
SELECT * FROM gemstones 
WHERE price < 5
ORDER BY carats DESC;

-- Meilleur rapport qualité/prix (prix par carat)
SELECT 
    article_id,
    title,
    gem_type,
    carats,
    price,
    ROUND(price / carats, 2) as prix_par_carat
FROM gemstones 
WHERE carats > 0
ORDER BY prix_par_carat ASC
LIMIT 20;


-- ============================================
-- RECHERCHES PAR COULEUR
-- ============================================

-- Pierres bleues
SELECT * FROM gemstones 
WHERE color = 'Blue'
ORDER BY price DESC;

-- Pierres rouges
SELECT * FROM gemstones 
WHERE color = 'Red'
ORDER BY price DESC;

-- Pierres vertes
SELECT * FROM gemstones 
WHERE color = 'Green'
ORDER BY price DESC;

-- Distribution des couleurs
SELECT color, COUNT(*) as nombre
FROM gemstones 
WHERE color IS NOT NULL
GROUP BY color
ORDER BY nombre DESC;


-- ============================================
-- ANALYSES AVANCÉES
-- ============================================

-- Prix moyen par type et origine
SELECT 
    gem_type,
    origin,
    COUNT(*) as nombre,
    AVG(price) as prix_moyen,
    SUM(carats) as total_carats
FROM gemstones 
WHERE gem_type IS NOT NULL AND origin IS NOT NULL
GROUP BY gem_type, origin
HAVING COUNT(*) >= 2
ORDER BY prix_moyen DESC;

-- Évolution des achats par mois
SELECT 
    DATE_TRUNC('month', purchase_date) as mois,
    COUNT(*) as nombre_achats,
    SUM(price) as montant_total
FROM gemstones 
WHERE purchase_date IS NOT NULL
GROUP BY mois
ORDER BY mois DESC;

-- Pierres achetées récemment (30 derniers jours)
SELECT * FROM gemstones 
WHERE purchase_date >= NOW() - INTERVAL '30 days'
ORDER BY purchase_date DESC;


-- ============================================
-- RECHERCHES COMBINÉES
-- ============================================

-- Saphirs bleus non chauffés de plus de 1 carat
SELECT * FROM gemstones 
WHERE gem_type = 'Sapphire'
  AND color = 'Blue'
  AND treatment = 'Unheated'
  AND carats >= 1.0
ORDER BY price DESC;

-- Rubis de Birmanie de haute clarté
SELECT * FROM gemstones 
WHERE gem_type = 'Ruby'
  AND origin IN ('Burma', 'Myanmar')
  AND clarity IN ('VS', 'VVS', 'IF', 'FL')
ORDER BY carats DESC;

-- Pierres rares (moins de 3 exemplaires par type+origine)
SELECT gem_type, origin, COUNT(*) as nombre
FROM gemstones 
WHERE gem_type IS NOT NULL AND origin IS NOT NULL
GROUP BY gem_type, origin
HAVING COUNT(*) < 3
ORDER BY nombre ASC;


-- ============================================
-- VALEUR DE LA COLLECTION
-- ============================================

-- Valeur par type de pierre
SELECT 
    gem_type,
    COUNT(*) as nombre,
    SUM(price) as valeur_totale,
    AVG(price) as prix_moyen,
    MIN(price) as prix_min,
    MAX(price) as prix_max
FROM gemstones 
WHERE gem_type IS NOT NULL
GROUP BY gem_type
ORDER BY valeur_totale DESC;

-- Répartition de la valeur (par tranche de prix)
SELECT 
    CASE 
        WHEN price < 5 THEN '0-5€'
        WHEN price < 10 THEN '5-10€'
        WHEN price < 20 THEN '10-20€'
        WHEN price < 50 THEN '20-50€'
        ELSE '50€+'
    END as tranche_prix,
    COUNT(*) as nombre,
    SUM(price) as valeur_totale
FROM gemstones
GROUP BY tranche_prix
ORDER BY MIN(price);


-- ============================================
-- MAINTENANCE
-- ============================================

-- Trouver les doublons potentiels (même titre)
SELECT title, COUNT(*) as nombre
FROM gemstones
GROUP BY title
HAVING COUNT(*) > 1;

-- Pierres sans image
SELECT * FROM gemstones 
WHERE image_url IS NULL AND local_image_path IS NULL;

-- Pierres avec données manquantes
SELECT 
    article_id,
    title,
    CASE WHEN gem_type IS NULL THEN 'Type manquant' END,
    CASE WHEN carats IS NULL THEN 'Carats manquant' END,
    CASE WHEN origin IS NULL THEN 'Origine manquante' END
FROM gemstones
WHERE gem_type IS NULL OR carats IS NULL OR origin IS NULL;


-- ============================================
-- EXPORT DE DONNÉES
-- ============================================

-- Export CSV de toutes les pierres (exécuter avec \copy en psql)
-- \copy (SELECT * FROM gemstones) TO 'export_gemstones.csv' WITH CSV HEADER;

-- Export CSV des saphirs uniquement
-- \copy (SELECT * FROM gemstones WHERE gem_type = 'Sapphire') TO 'export_saphirs.csv' WITH CSV HEADER;
