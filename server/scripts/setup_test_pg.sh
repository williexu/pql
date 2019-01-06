#!/usr/bin/env sh

psql postgres://postgres@localhost -f ./setup_test_dbs.sql
psql postgres://pql_test:pql_test@localhost/pql_test_1 -f ./setup_db_1.sql
psql postgres://pql_test:pql_test@localhost/pql_test_2 -f ./setup_db_2.sql
