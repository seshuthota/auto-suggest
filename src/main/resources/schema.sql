-- Base table for people and name index (SQLite)
CREATE TABLE IF NOT EXISTS people (
  id          INTEGER PRIMARY KEY,
  name        TEXT NOT NULL,
  popularity  INTEGER NOT NULL DEFAULT 0
);

-- Case-insensitive index for ASCII; keeps LIKE 'q%' indexable
CREATE INDEX IF NOT EXISTS idx_people_name_nocase ON people(name COLLATE NOCASE);

-- Optional FTS5 external-content table for richer search in SQLite
CREATE VIRTUAL TABLE IF NOT EXISTS people_fts USING fts5(
  name,
  content='people', content_rowid='id',
  tokenize = 'unicode61 remove_diacritics 2'
);

-- Initial sync: populate FTS from base table (safe if empty)
INSERT INTO people_fts(rowid, name)
  SELECT id, name FROM people WHERE id NOT IN (SELECT rowid FROM people_fts);

-- Sample seed data for quick testing (idempotent-ish)
INSERT INTO people(id, name)
SELECT 1, 'Microsoft' WHERE NOT EXISTS (SELECT 1 FROM people WHERE id = 1);
INSERT INTO people(id, name)
SELECT 2, 'Microtek' WHERE NOT EXISTS (SELECT 1 FROM people WHERE id = 2);
INSERT INTO people(id, name)
SELECT 3, 'Microscopy Society' WHERE NOT EXISTS (SELECT 1 FROM people WHERE id = 3);
INSERT INTO people(id, name)
SELECT 4, 'Minecraft' WHERE NOT EXISTS (SELECT 1 FROM people WHERE id = 4);
INSERT INTO people(id, name)
SELECT 5, 'Macrohard' WHERE NOT EXISTS (SELECT 1 FROM people WHERE id = 5);
