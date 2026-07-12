update users
set username = lower(btrim(username)),
    email = lower(btrim(email));

alter table users
    add column if not exists version bigint not null default 0;

create unique index if not exists ux_users_normalized_username
    on users ((lower(btrim(username))));

create unique index if not exists ux_users_normalized_email
    on users ((lower(btrim(email))));

create index if not exists ix_users_enabled_created_at
    on users (enabled, created_at desc);

create index if not exists ix_users_account_state
    on users (enabled, account_non_locked);
