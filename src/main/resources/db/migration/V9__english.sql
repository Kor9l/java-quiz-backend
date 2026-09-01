-- The English module: a vocabulary the learner drills, kept beside the backend material rather
-- than in its own service. Words live in groups, and a group is one of two things:
--
--   PUBLIC   — shipped with the app or curated by an admin, visible to everybody
--   PERSONAL — created by one learner from their own text, visible only to them
--
-- That single distinction is what the whole access story rests on: a user reads every PUBLIC
-- group plus their own PERSONAL ones, and edits their own PERSONAL ones plus — as an admin —
-- the PUBLIC ones.

CREATE TABLE word_groups (
    id UUID PRIMARY KEY,
    -- Stable handle for a group, so seeded content can be recognised across databases.
    code VARCHAR(255) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    group_type VARCHAR(16) NOT NULL,
    -- Set for PERSONAL groups, NULL for PUBLIC ones.
    owner_id UUID REFERENCES users (id) ON DELETE CASCADE,
    sort_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX word_groups_owner_idx ON word_groups (owner_id);

CREATE TABLE words (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES word_groups (id) ON DELETE CASCADE,
    sort_order INT NOT NULL,
    text TEXT NOT NULL,
    translation TEXT NOT NULL,
    example TEXT,
    -- Raised by the `12* word — translation` marker a bulk import understands: something the
    -- learner has only just met and wants shown differently.
    is_new BOOLEAN NOT NULL DEFAULT FALSE,
    -- Answer history carried over from the app this module came from. Nothing writes to them
    -- yet — the drilling loop is not part of this move — but the numbers are real and would be
    -- gone for good if the import dropped them.
    correct_count INT NOT NULL DEFAULT 0,
    incorrect_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX words_group_idx ON words (group_id, sort_order);

-- Per user, unlike the answer counts above: a shared group is starred by one learner without
-- the others noticing.
CREATE TABLE word_favorites (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    word_id UUID NOT NULL REFERENCES words (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, word_id)
);
