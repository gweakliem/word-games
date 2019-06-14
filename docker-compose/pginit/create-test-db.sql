-- used when creating the local dev pg container
CREATE ROLE "ktor-demo-test" WITH SUPERUSER LOGIN PASSWORD 'ktor-demo-test';
CREATE DATABASE "ktor-demo-test" OWNER 'ktor-demo-test';
