-- A third practice track: grammar. Where a SQL exercise is a dataset plus a query and a Java
-- exercise is a class plus the calls made against it, a grammar exercise is a text plus the
-- answers that count as right — so the columns differ, but the track, the difficulty, the
-- progress, the sources and the link back to the study material do not, and those are the ones
-- the API navigates by. The table is therefore widened a second time rather than duplicated,
-- exactly as V12 widened it for Java, and which half is filled follows from practice_tasks.track.
--
-- What is genuinely new here is grading, and it is the cheapest of the three: nothing is run, so
-- there is no sandbox. An answer is a string, compared with the accepted ones after normalising
-- both sides.

-- Which kind of exercise this is, and therefore how its answer is read. WORD_ORDER is the only
-- kind implemented; GAP_FILL, CHOICE_IN_CONTEXT, TRANSFORM, ERROR_CORRECTION and TRANSLATE_RU_EN
-- are planned and arrive with content that needs them, not before.
ALTER TABLE practice_tasks ADD COLUMN kind VARCHAR(24);

-- The text the exercise is about. Its shape follows the kind:
--   WORD_ORDER — the shuffled chunks, separated by " / ", in the order the handout printed them;
--                joining them in the right order is the answer. A chunk may be several words
--                ("a long time"), which is why they are stored as chunks and not as words.
--   GAP_FILL and the rest — the sentence with {{1}}, {{2}} markers where the blanks are.
-- One column because it is one thing: the material under discussion, kept out of the statement
-- prose so the client can render it as a block. Same reasoning as questions.code, which holds a
-- code listing for a backend question and the sentence under discussion for a grammar one.
ALTER TABLE practice_tasks ADD COLUMN sentence TEXT;

-- Case is part of the answer only where the exercise is about case — capitals and punctuation.
-- Everywhere else a learner who typed a lower-case first word got the grammar right, and being
-- told otherwise teaches them nothing.
ALTER TABLE practice_tasks ADD COLUMN case_sensitive BOOLEAN NOT NULL DEFAULT FALSE;

-- Every answer that counts as right, one row each, so an exercise with two good orderings is
-- content rather than a special case in the matcher.
--
-- blank_index says which blank of the sentence an answer belongs to, and is what lets feedback
-- come back per blank instead of per exercise ("the second blank is wrong", not "wrong").
-- WORD_ORDER has exactly one blank, numbered 1: the whole assembled phrase.
CREATE TABLE practice_task_blanks (
    task_id VARCHAR(128) NOT NULL REFERENCES practice_tasks (id) ON DELETE CASCADE,
    sort_order INT NOT NULL,
    blank_index INT NOT NULL,
    accepted TEXT NOT NULL,
    PRIMARY KEY (task_id, sort_order)
);

CREATE INDEX practice_task_blanks_blank_idx ON practice_task_blanks (task_id, blank_index);

-- All three halves have to hold together, and the CHECK is where that is said once. Without the
-- new branch the first grammar INSERT would be refused, since the constraint V12 left behind
-- knows only two tracks.
--
-- The grammar branch also requires the link to the study material, which the other two leave
-- optional. It is not decoration here: practice_tasks has no level column, and a grammar
-- exercise takes its level from the section it drills — an exercise with no section would be an
-- exercise with no level, in a module where a wrong level means an empty round rather than an
-- odd one.
ALTER TABLE practice_tasks DROP CONSTRAINT practice_tasks_track_shape;
ALTER TABLE practice_tasks ADD CONSTRAINT practice_tasks_track_shape CHECK (
    (track = 'sql' AND dataset_id IS NOT NULL AND solution_sql IS NOT NULL)
    OR (track = 'java' AND class_name IS NOT NULL AND solution_code IS NOT NULL)
    OR (track = 'grammar' AND kind IS NOT NULL AND sentence IS NOT NULL
        AND topic_id IS NOT NULL AND section_id IS NOT NULL)
);
