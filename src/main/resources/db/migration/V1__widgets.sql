 CREATE TABLE widgets (
    id         SERIAL NOT NULL PRIMARY KEY,
    name       TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
)
