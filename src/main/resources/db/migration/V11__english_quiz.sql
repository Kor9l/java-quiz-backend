-- The English drilling loop, laid out like the backend quiz it is modelled on: one row per
-- running session with its whole state in JSONB, and one stats row per learner.
--
-- Separate tables rather than a `module` column on quiz_sessions and user_stats. The two quizzes
-- ask different things — a question with fixed options versus a word with options generated per
-- round — and their stats break down along different axes (topic/section against group/word).
-- Sharing a table would mean every column being null for one of them.

CREATE TABLE word_quiz_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    stage VARCHAR(32) NOT NULL,
    finished BOOLEAN NOT NULL DEFAULT FALSE,
    payload JSONB NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX word_quiz_sessions_user_active_idx ON word_quiz_sessions (user_id, finished);

-- Per learner, unlike the correct_count / incorrect_count that came over with the imported
-- words. Those are one global pair per word and would have every learner writing over each
-- other on a shared group; they stay as the history they are, and answering writes here.
CREATE TABLE word_stats (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    total_answered INT NOT NULL DEFAULT 0,
    total_correct INT NOT NULL DEFAULT 0,
    payload JSONB NOT NULL
);
