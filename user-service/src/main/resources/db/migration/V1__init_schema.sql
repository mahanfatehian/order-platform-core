create table if not exists users (
                                     id uuid primary key,
                                     username varchar(100) not null unique,
    email varchar(150) not null unique,
    password_hash varchar(255) not null,
    enabled boolean not null default true,
    account_non_locked boolean not null default true,
    first_name varchar(100),
    last_name varchar(100),
    created_at timestamptz not null,
    updated_at timestamptz not null
    );

create table if not exists roles (
                                     id uuid primary key,
                                     name varchar(100) not null unique
    );

create table if not exists authorities (
                                           id uuid primary key,
                                           name varchar(100) not null unique
    );

create table if not exists user_roles (
                                          user_id uuid not null references users(id) on delete cascade,
    role_id uuid not null references roles(id) on delete cascade,
    primary key (user_id, role_id)
    );

create table if not exists role_authorities (
                                                role_id uuid not null references roles(id) on delete cascade,
    authority_id uuid not null references authorities(id) on delete cascade,
    primary key (role_id, authority_id)
    );
