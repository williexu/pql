create role pql_test with login createdb password 'pql_test';
create database pql_test_1 owner pql_test;
create database pql_test_2 owner pql_test;
