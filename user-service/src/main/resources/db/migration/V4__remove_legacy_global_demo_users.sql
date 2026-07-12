-- V2 predated profile-specific seed support. Remove only its fixed demo identities;
-- the dev-profile initializer recreates documented demo users outside production.
delete from users
where (id = 'c1111111-1111-1111-1111-111111111111'
       and username = 'johndoe'
       and email = 'john.doe@example.com'
       and password_hash = '$2a$10$sukq5t4rYyULEUU1FWKMye1JSKtb4yz5Mv3f6prdbx0QuHXdzQoYm')
   or (id = 'c2222222-2222-2222-2222-222222222222'
       and username = 'admin'
       and email = 'admin@example.com'
       and password_hash = '$2a$10$4GCNnD4Yr8NGUnfpu5vQ8urLeZ0F4jCOOmKgL77MD5qFeD1YNAZj6');
