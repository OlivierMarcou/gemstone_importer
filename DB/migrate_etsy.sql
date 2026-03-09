-- ============================================================
-- Migration : Fiches produit Etsy + liaison avec gemstones
-- ============================================================

-- Type de bijou
DO $$ BEGIN
    CREATE TYPE jewelry_type AS ENUM (
        'ring',          -- Bague
        'earrings',      -- Boucles d''oreilles
        'pendant',       -- Pendentif
        'necklace',      -- Collier
        'bracelet',      -- Bracelet
        'brooch',        -- Broche
        'anklet',        -- Bracelet de cheville
        'tiara',         -- Tiare
        'other'          -- Autre
    );
EXCEPTION WHEN duplicate_object THEN null; END $$;

-- Statut de la fiche produit Etsy
DO $$ BEGIN
    CREATE TYPE etsy_listing_status AS ENUM (
        'draft',         -- Brouillon
        'active',        -- Active (en vente)
        'sold',          -- Vendue
        'inactive',      -- Désactivée
        'expired'        -- Expirée
    );
EXCEPTION WHEN duplicate_object THEN null; END $$;

-- ============================================================
-- Table principale : fiches produit Etsy
-- ============================================================
CREATE TABLE IF NOT EXISTS etsy_listings (
    id                  SERIAL PRIMARY KEY,
    listing_ref         VARCHAR(50)  UNIQUE NOT NULL,    -- Référence interne ex: ETY-2024-001
    etsy_listing_id     VARCHAR(30)  UNIQUE,             -- ID numérique Etsy (optionnel)
    title               TEXT         NOT NULL,
    description         TEXT,
    jewelry_type        jewelry_type NOT NULL DEFAULT 'other',
    status              etsy_listing_status NOT NULL DEFAULT 'draft',
    price               DECIMAL(10,2),                   -- Prix de vente Etsy
    currency            VARCHAR(3)   DEFAULT 'EUR',
    quantity            INTEGER      DEFAULT 1,
    etsy_url            TEXT,                            -- URL de l'annonce Etsy
    tags                TEXT[],                          -- Tags Etsy (tableau)
    materials           TEXT[],                          -- Matériaux (or, argent, etc.)
    metal_type          VARCHAR(50),                     -- Type de métal principal
    metal_color         VARCHAR(30),                     -- Couleur métal (yellow, white, rose)
    ring_size           VARCHAR(20),                     -- Taille de bague si applicable
    weight_grams        DECIMAL(8,2),                    -- Poids total en grammes
    main_image_url      TEXT,                            -- Image principale
    main_image_path     TEXT,                            -- Chemin local image
    processing_days     INTEGER      DEFAULT 3,          -- Délai de fabrication
    views               INTEGER      DEFAULT 0,
    favorites           INTEGER      DEFAULT 0,
    sales_count         INTEGER      DEFAULT 0,
    created_at          TIMESTAMP    DEFAULT NOW(),
    updated_at          TIMESTAMP    DEFAULT NOW(),
    listed_at           TIMESTAMP,                       -- Date de mise en ligne
    sold_at             TIMESTAMP                        -- Date de vente
);

-- ============================================================
-- Table de liaison : gemstones ↔ etsy_listings  (N:N)
-- ============================================================
CREATE TABLE IF NOT EXISTS listing_gemstones (
    id              SERIAL PRIMARY KEY,
    listing_id      INTEGER NOT NULL REFERENCES etsy_listings(id) ON DELETE CASCADE,
    gemstone_id     INTEGER NOT NULL REFERENCES gemstones(id)     ON DELETE RESTRICT,
    position        INTEGER DEFAULT 1,   -- Ordre d'affichage (pierre principale = 1)
    notes           TEXT,                -- Note sur le rôle de la pierre dans le bijou
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE (listing_id, gemstone_id)
);

-- Index pour les recherches fréquentes
CREATE INDEX IF NOT EXISTS idx_listing_gemstones_listing   ON listing_gemstones(listing_id);
CREATE INDEX IF NOT EXISTS idx_listing_gemstones_gemstone  ON listing_gemstones(gemstone_id);
CREATE INDEX IF NOT EXISTS idx_etsy_listings_ref           ON etsy_listings(listing_ref);
CREATE INDEX IF NOT EXISTS idx_etsy_listings_status        ON etsy_listings(status);
CREATE INDEX IF NOT EXISTS idx_etsy_listings_type          ON etsy_listings(jewelry_type);

-- ============================================================
-- Vue pratique : fiches avec nombre de pierres
-- ============================================================
CREATE OR REPLACE VIEW etsy_listings_summary AS
SELECT
    el.*,
    COUNT(DISTINCT lg.gemstone_id) AS gemstone_count,
    STRING_AGG(DISTINCT g.gem_type, ', ' ORDER BY g.gem_type) AS gem_types_used
FROM etsy_listings el
LEFT JOIN listing_gemstones lg ON lg.listing_id = el.id
LEFT JOIN gemstones g ON g.id = lg.gemstone_id
GROUP BY el.id;

-- ============================================================
-- Exemples de données (optionnel, commenter si non souhaité)
-- ============================================================
-- INSERT INTO etsy_listings (listing_ref, title, jewelry_type, status, price, metal_type, metal_color)
-- VALUES
--   ('ETY-2024-001', 'Bague saphir bleu naturel 1.5ct or jaune 18k', 'ring', 'draft', 450.00, 'Or 18k', 'yellow'),
--   ('ETY-2024-002', 'Boucles rubis non chauffés argent 925', 'earrings', 'draft', 320.00, 'Argent 925', 'white');

-- Vérification
SELECT 'etsy_listings créée' AS info
WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='etsy_listings');
SELECT 'listing_gemstones créée' AS info
WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='listing_gemstones');
