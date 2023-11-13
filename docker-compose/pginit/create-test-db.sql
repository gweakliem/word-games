-- used when creating the local dev pg container
CREATE ROLE "word-games-test" WITH SUPERUSER LOGIN PASSWORD 'word-games-test';
CREATE DATABASE "word-games-test" OWNER 'word-games-test';
