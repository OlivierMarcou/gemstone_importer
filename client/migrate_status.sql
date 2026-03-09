-- Migration : ajout du champ status à la table gemstones
-- À exécuter une seule fois sur votre base de données

-- 1. Créer le type ENUM pour le statut
DO $$ BEGIN
    CREATE TYPE gem_status AS ENUM ('in_stock', 'used', 'damaged', 'unavailable');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 2. Ajouter la colonne status (nullable pour commencer)
ALTER TABLE gemstones
    ADD COLUMN IF NOT EXISTS status gem_status;

-- 3. Initialiser les valeurs :
--    - "unavailable" si pas de prix
--    - "in_stock" sinon (valeur par défaut)
UPDATE gemstones
SET status = CASE
    WHEN price IS NULL OR price = 0 THEN 'unavailable'::gem_status
    ELSE 'in_stock'::gem_status
END
WHERE status IS NULL;

-- 4. Définir la valeur par défaut et rendre NOT NULL
ALTER TABLE gemstones
    ALTER COLUMN status SET DEFAULT 'in_stock',
    ALTER COLUMN status SET NOT NULL;

-- Vérification
SELECT status, COUNT(*) as count FROM gemstones GROUP BY status ORDER BY status;
