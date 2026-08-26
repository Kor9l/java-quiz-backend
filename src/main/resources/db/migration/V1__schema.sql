CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    display_name VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    auth_provider VARCHAR(20) NOT NULL,
    google_id VARCHAR(255) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE topics (
    id VARCHAR(64) PRIMARY KEY,
    sort_order INT NOT NULL,
    name_en TEXT NOT NULL,
    name_ru TEXT NOT NULL
);

CREATE TABLE sections (
    topic_id VARCHAR(64) NOT NULL REFERENCES topics (id) ON DELETE CASCADE,
    id VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL,
    title_en TEXT NOT NULL,
    title_ru TEXT NOT NULL,
    PRIMARY KEY (topic_id, id)
);

CREATE TABLE questions (
    id VARCHAR(128) PRIMARY KEY,
    topic_id VARCHAR(64) NOT NULL,
    section_id VARCHAR(64) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    text_en TEXT NOT NULL,
    text_ru TEXT NOT NULL,
    code TEXT,
    explanation_en TEXT NOT NULL,
    explanation_ru TEXT NOT NULL,
    FOREIGN KEY (topic_id, section_id) REFERENCES sections (topic_id, id)
);

CREATE INDEX questions_topic_section_idx ON questions (topic_id, section_id);

CREATE TABLE question_options (
    question_id VARCHAR(128) NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    option_index INT NOT NULL,
    text_en TEXT NOT NULL,
    text_ru TEXT NOT NULL,
    correct BOOLEAN NOT NULL,
    PRIMARY KEY (question_id, option_index)
);

CREATE TABLE question_sources (
    question_id VARCHAR(128) NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    sort_order INT NOT NULL,
    url TEXT NOT NULL,
    PRIMARY KEY (question_id, sort_order)
);

CREATE TABLE material_sections (
    topic_id VARCHAR(64) NOT NULL,
    section_id VARCHAR(64) NOT NULL,
    estimated_minutes INT NOT NULL,
    summary_en TEXT NOT NULL,
    summary_ru TEXT NOT NULL,
    body_en TEXT NOT NULL,
    body_ru TEXT NOT NULL,
    PRIMARY KEY (topic_id, section_id),
    FOREIGN KEY (topic_id, section_id) REFERENCES sections (topic_id, id)
);

CREATE TABLE material_sources (
    topic_id VARCHAR(64) NOT NULL,
    section_id VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    PRIMARY KEY (topic_id, section_id, sort_order),
    FOREIGN KEY (topic_id, section_id) REFERENCES material_sections (topic_id, section_id) ON DELETE CASCADE
);

CREATE TABLE user_settings (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    payload JSONB NOT NULL
);

CREATE TABLE user_stats (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    total_answered INT NOT NULL DEFAULT 0,
    total_correct INT NOT NULL DEFAULT 0,
    payload JSONB NOT NULL
);

CREATE TABLE user_progress (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    payload JSONB NOT NULL
);

CREATE TABLE quiz_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    stage VARCHAR(32) NOT NULL,
    finished BOOLEAN NOT NULL DEFAULT FALSE,
    payload JSONB NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX quiz_sessions_user_active_idx ON quiz_sessions (user_id, finished);
