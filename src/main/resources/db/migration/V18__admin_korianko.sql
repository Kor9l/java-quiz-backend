-- A second admin, so the person who runs this instance can curate the PUBLIC word groups from
-- their own account instead of the seeded admin@javaquiz.local one. ADMIN is the whole grant:
-- a PUBLIC group is readable by everybody and editable by admins, and PERSONAL groups stay with
-- their owner either way, so the role is exactly the right to edit any shared group.
--
-- Promote-or-seed rather than a plain UPDATE, because this account signs in with Google and the
-- row may not exist yet — a migration runs once, so an UPDATE that matched nothing would leave
-- nothing behind. The seeded row carries the email, no password and no google_id;
-- AuthService.loginWithGoogle() finds an existing account by email before creating one, links
-- the Google identity to it and leaves the role alone, so the first sign-in adopts this row
-- rather than making a second USER account beside it.
--
-- Matched on lower(email) because that is the shape every account is stored in: register() and
-- loginWithGoogle() both normalise before saving.

UPDATE users SET role = 'ADMIN' WHERE lower(email) = 'korianko@gmail.com';

-- password_hash stays NULL, and the provider says GOOGLE: login() accepts neither, so the row
-- is unusable until the Google sign-in it is waiting for.
INSERT INTO users (id, email, password_hash, display_name, role, auth_provider)
SELECT gen_random_uuid(), 'korianko@gmail.com', NULL, 'Korianko', 'ADMIN', 'GOOGLE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE lower(email) = 'korianko@gmail.com');

-- The three state rows AuthService.createUserState() writes at sign-up. An account seeded around
-- it has to get them here: saveSettings() answers 404 without one rather than creating it.
INSERT INTO user_settings (user_id, payload)
SELECT id, '{}'::jsonb FROM users WHERE lower(email) = 'korianko@gmail.com'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_stats (user_id, total_answered, total_correct, payload)
SELECT id, 0, 0, '{}'::jsonb FROM users WHERE lower(email) = 'korianko@gmail.com'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_progress (user_id, payload)
SELECT id, '{}'::jsonb FROM users WHERE lower(email) = 'korianko@gmail.com'
ON CONFLICT (user_id) DO NOTHING;
