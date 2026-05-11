insert into authorities (id, name) values
                                       ('11111111-1111-1111-1111-111111111111', 'profile:read'),
                                       ('22222222-2222-2222-2222-222222222222', 'profile:update'),
                                       ('33333333-3333-3333-3333-333333333333', 'user:manage')
    on conflict do nothing;

insert into roles (id, name) values
                                 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ROLE_USER'),
                                 ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'ROLE_ADMIN')
    on conflict do nothing;

insert into role_authorities (role_id, authority_id) values
                                                         ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111'),
                                                         ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '22222222-2222-2222-2222-222222222222'),
                                                         ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '11111111-1111-1111-1111-111111111111'),
                                                         ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222'),
                                                         ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '33333333-3333-3333-3333-333333333333')
    on conflict do nothing;

insert into users (
    id,
    username,
    email,
    password_hash,
    enabled,
    account_non_locked,
    first_name,
    last_name,
    created_at,
    updated_at
) values
      (
          'c1111111-1111-1111-1111-111111111111',
          'johndoe',
          'john.doe@example.com',
          '$2a$10$replace_with_real_bcrypt_hash',
          true,
          true,
          'John',
          'Doe',
          now(),
          now()
      ),
      (
          'c2222222-2222-2222-2222-222222222222',
          'admin.smith',
          'admin.smith@example.com',
          '$2a$10$replace_with_real_bcrypt_hash',
          true,
          true,
          'Admin',
          'Smith',
          now(),
          now()
      )
    on conflict do nothing;

insert into user_roles (user_id, role_id) values
                                              ('c1111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
                                              ('c2222222-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb')
    on conflict do nothing;
