CREATE TABLE words {
    id          SERIAL NOT NULL PRIMARY KEY,
    word        TEXT,
    index_key   LONG,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
}