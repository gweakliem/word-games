CREATE TABLE widgets (
  id serial NOT NULL PRIMARY KEY,
  name varchar(100) NOT NULL,
  created_at timestamp without time zone NOT NULL default now()
)
