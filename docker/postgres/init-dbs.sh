#!/bin/bash
set -e

echo "Criando bancos de dados legado_db e novo_db..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE legado_db;
    CREATE DATABASE novo_db;
    GRANT ALL PRIVILEGES ON DATABASE legado_db TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON DATABASE novo_db TO $POSTGRES_USER;
EOSQL
echo "Bancos de dados inicializados com sucesso!"
