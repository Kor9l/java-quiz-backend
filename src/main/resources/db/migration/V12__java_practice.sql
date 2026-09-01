-- A second practice track. Where a SQL exercise is a dataset plus a query, a Java exercise is
-- a class the learner writes plus the calls made against it — so the columns differ, but the
-- track, the difficulty, the progress, the sources and the link back to the study material do
-- not, and those are the ones the API navigates by. The task table is therefore widened rather
-- than duplicated, and the two halves of it are exclusive: which one is filled follows from
-- practice_tasks.track.

ALTER TABLE practice_tasks ALTER COLUMN dataset_id DROP NOT NULL;
ALTER TABLE practice_tasks ALTER COLUMN solution_sql DROP NOT NULL;

-- The top-level class a submission has to declare. The harness calls it by this name, so a
-- submission that declares something else is rejected rather than left to fail confusingly.
ALTER TABLE practice_tasks ADD COLUMN class_name VARCHAR(128);
ALTER TABLE practice_tasks ADD COLUMN starter_code TEXT;
-- The reference answer. What it returns for each case is what a submission is compared
-- against, so any implementation reaching the same values counts as correct.
ALTER TABLE practice_tasks ADD COLUMN solution_code TEXT;

-- The calls a Java task is graded by, run in order against the reference and then against the
-- submission. Each expression is compiled into a generated harness, which is why it is content
-- rather than anything a user can supply.
CREATE TABLE practice_task_cases (
    task_id VARCHAR(128) NOT NULL REFERENCES practice_tasks (id) ON DELETE CASCADE,
    sort_order INT NOT NULL,
    label TEXT NOT NULL,
    expression TEXT NOT NULL,
    PRIMARY KEY (task_id, sort_order)
);

-- Both halves have to hold together: a SQL task needs its dataset and its query, a Java task
-- needs its class and its solution, and neither may be half-populated.
ALTER TABLE practice_tasks ADD CONSTRAINT practice_tasks_track_shape CHECK (
    (track = 'sql' AND dataset_id IS NOT NULL AND solution_sql IS NOT NULL)
    OR (track = 'java' AND class_name IS NOT NULL AND solution_code IS NOT NULL)
);
