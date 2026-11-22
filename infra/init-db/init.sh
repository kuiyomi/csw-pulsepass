#!/bin/sh
set -eu
for db in keycloak inventory iam orders tickets analytics; do
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
        --set=db="$db" --set=pw="$SERVICE_DB_PASSWORD" <<'SQL'
CREATE ROLE :"db" LOGIN PASSWORD :'pw';
CREATE DATABASE :"db" OWNER :"db";
REVOKE CONNECT ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO :"db";
SQL
done
