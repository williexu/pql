create sequence people_id_seq;
create table people (
  name text primary key,
  age int,
  age_precise double precision,
  attributes jsonb,
  birthday timestamp,
  siblings text[],

  -- first-level support for these types
  varchar_col varchar,
  varchar10_col varchar(10),
  varchar16_col varchar(16),
  uuid_col uuid,
  date_col date,
  json_col json,
  tstz_col timestamptz
);

create table pets (
  name text,
  owner text not null references people(name)
);

create table cars (
  model text,
  owner text not null references people(name)
);
