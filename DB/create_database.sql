-- Script pour créer la base de données PostgreSQL
-- Exécutez ce script en tant qu'utilisateur postgres avant la première utilisation

-- Créer la base de données
CREATE DATABASE gemstones
    WITH 
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'fr_FR.UTF-8'
    LC_CTYPE = 'fr_FR.UTF-8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

-- Se connecter à la base de données
\c gemstones

-- Le schéma sera créé automatiquement par l'application Java
-- Ce script crée juste la base de données

COMMENT ON DATABASE gemstones IS 'Base de données pour la gestion de pierres précieuses';
