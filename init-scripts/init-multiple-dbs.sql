SELECT 'CREATE DATABASE userdb'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'userdb')
\gexec

SELECT 'CREATE DATABASE orderdb'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'orderdb')
\gexec

SELECT 'CREATE DATABASE storedb'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'storedb')
\gexec
