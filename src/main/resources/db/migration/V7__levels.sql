-- Career level, alongside the difficulty a question already carries. The two are orthogonal:
-- difficulty says how tricky a question is, level says who is expected to know the material at
-- all. "What does volatile guarantee" is an easy question at middle level; false sharing is a
-- medium one at senior level.
--
-- Everything loaded by V2 and V6 was written for a middle-level reader, so the default
-- backfills the existing 294 questions and 49 sections correctly. It stays in place afterwards
-- because content tests, not the database, are what hold new content to an explicit level.

ALTER TABLE questions ADD COLUMN level VARCHAR(16) NOT NULL DEFAULT 'MIDDLE';
ALTER TABLE sections ADD COLUMN level VARCHAR(16) NOT NULL DEFAULT 'MIDDLE';

-- The quiz pool is always filtered by topic and level together.
CREATE INDEX questions_topic_level_idx ON questions (topic_id, level);
