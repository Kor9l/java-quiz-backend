-- Practice section: hands-on exercises graded by running them, as opposed to the
-- multiple-choice quiz. The first track is SQL.

CREATE TABLE practice_datasets (
    id VARCHAR(64) PRIMARY KEY,
    sort_order INT NOT NULL,
    title_en TEXT NOT NULL,
    title_ru TEXT NOT NULL,
    description_en TEXT NOT NULL,
    description_ru TEXT NOT NULL
);

-- DDL and seed statements building a dataset, replayed in order into a throwaway
-- in-memory database every time a submission is graded.
CREATE TABLE practice_dataset_statements (
    dataset_id VARCHAR(64) NOT NULL REFERENCES practice_datasets (id) ON DELETE CASCADE,
    sort_order INT NOT NULL,
    sql_text TEXT NOT NULL,
    PRIMARY KEY (dataset_id, sort_order)
);

CREATE TABLE practice_tasks (
    id VARCHAR(128) PRIMARY KEY,
    track VARCHAR(32) NOT NULL,
    dataset_id VARCHAR(64) NOT NULL REFERENCES practice_datasets (id),
    difficulty VARCHAR(16) NOT NULL,
    sort_order INT NOT NULL,
    -- The study material section this exercise drills, so a stuck learner can go and read
    -- about it. Nullable: an exercise does not have to belong to a section.
    topic_id VARCHAR(64),
    section_id VARCHAR(64),
    title_en TEXT NOT NULL,
    title_ru TEXT NOT NULL,
    statement_en TEXT NOT NULL,
    statement_ru TEXT NOT NULL,
    hint_en TEXT,
    hint_ru TEXT,
    starter_sql TEXT,
    -- The reference answer. Its result set is what a submission is compared against,
    -- so any query reaching the same rows counts as correct.
    solution_sql TEXT NOT NULL,
    order_matters BOOLEAN NOT NULL DEFAULT FALSE,
    explanation_en TEXT NOT NULL,
    explanation_ru TEXT NOT NULL
);

CREATE INDEX practice_tasks_track_idx ON practice_tasks (track, difficulty, sort_order);

CREATE TABLE practice_task_sources (
    task_id VARCHAR(128) NOT NULL REFERENCES practice_tasks (id) ON DELETE CASCADE,
    sort_order INT NOT NULL,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    PRIMARY KEY (task_id, sort_order)
);

CREATE TABLE practice_progress (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    task_id VARCHAR(128) NOT NULL REFERENCES practice_tasks (id) ON DELETE CASCADE,
    attempts INT NOT NULL DEFAULT 0,
    solved BOOLEAN NOT NULL DEFAULT FALSE,
    solved_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    last_sql TEXT,
    PRIMARY KEY (user_id, task_id)
);

CREATE INDEX practice_progress_user_idx ON practice_progress (user_id);
