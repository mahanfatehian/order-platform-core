create table addresses (
    id uuid primary key,
    user_id uuid not null references users (id),
    recipient varchar(200) not null,
    line1 varchar(200) not null,
    line2 varchar(200),
    city varchar(100) not null,
    region varchar(100) not null,
    postal_code varchar(20) not null,
    phone varchar(32) not null,
    country varchar(2) not null,
    default_address boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_addresses_country_ir check (country = 'IR')
);

create index idx_addresses_user on addresses (user_id);

create unique index uq_addresses_one_default_per_user
    on addresses (user_id)
    where default_address;
