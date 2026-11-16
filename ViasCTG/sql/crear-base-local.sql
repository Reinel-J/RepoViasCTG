-- Crea (o actualiza) el usuario y las bases de la réplica SQL en un PostgreSQL local. Lo corre una persona con el
-- usuario administrador "postgres"; la contraseña de "viasctg" llega por la variable de entorno SQL_PASSWORD, así no
-- queda escrita en este archivo ni en el historial de la terminal. Se puede ejecutar varias veces.
--
--   $env:SQL_PASSWORD = '<la de app.sql.password en local.properties>'
--   & "C:\Program Files\PostgreSQL\17\bin\psql.exe" -h localhost -U postgres -d postgres -f sql\crear-base-local.sql

\set ON_ERROR_STOP on
\set clave ''
\getenv clave SQL_PASSWORD

-- Sin contraseña no se sigue (\gset deja "error" sin definir cuando el resultado es NULL).
SELECT CASE WHEN :'clave' = '' THEN 'Falta la variable de entorno SQL_PASSWORD' END AS error \gset
\if :{?error}
  \echo :error
  \quit
\endif

-- Usuario de la aplicación: solo puede iniciar sesión; no es superusuario ni crea bases.
SELECT format('CREATE ROLE viasctg LOGIN PASSWORD %L', :'clave')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'viasctg') \gexec
SELECT format('ALTER ROLE viasctg WITH LOGIN PASSWORD %L', :'clave') \gexec

-- viasctg_sql: la réplica que usa la aplicación. viasctg_sql_test: la usan solo las pruebas de integración.
SELECT 'CREATE DATABASE viasctg_sql OWNER viasctg ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'viasctg_sql') \gexec
SELECT 'CREATE DATABASE viasctg_sql_test OWNER viasctg ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'viasctg_sql_test') \gexec

\echo 'Listo: usuario viasctg y bases viasctg_sql y viasctg_sql_test.'
