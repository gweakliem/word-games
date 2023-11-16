CREATE TABLE words (
   id SERIAL  NOT NULL PRIMARY KEY,
   word       TEXT,
   hash       BIGINT,
   created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
)