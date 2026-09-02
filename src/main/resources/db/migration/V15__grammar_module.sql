-- Which of the two things this app teaches a topic belongs to.
--
-- Until now every topic was backend material, so every listing could return all of them.
-- English grammar arrives as topics of exactly the same shape — a topic of sections, an article
-- per section, six questions per section — and that sameness is the point: it reuses the quiz,
-- the read state and the stats rather than growing a second engine. What it must not reuse is
-- the audience. Without a discriminator the grammar courses would appear in the backend's topic
-- list, in its "all topics" quiz pool and as empty rows in its stats breakdown.
--
-- Grammar is one half of the English module; the vocabulary trainer is the other and keeps its
-- own tables, so it never appears here.
--
-- The default backfills the seven existing topics correctly, and it stays afterwards rather
-- than being dropped: V2, V6 and V8 insert named column lists that have already run everywhere,
-- and a topic arriving without a module is far more likely to be backend material than not.
ALTER TABLE topics ADD COLUMN module VARCHAR(16) NOT NULL DEFAULT 'BACKEND';

-- The area of grammar a section drills — tenses, articles, modals, syntax. NULL for backend
-- sections, which are grouped by the topic they sit in already.
--
-- A grammar course runs one level end to end, in teaching order, because a learner who opens
-- "base" wants to read it through; that makes the level the cut the courses are made along.
-- This column keeps the other cut — every conditional across all three levels — reachable later
-- without moving a single section.
ALTER TABLE sections ADD COLUMN area VARCHAR(32);

-- Every listing of topics is now module plus order, and so is the sections lookup that goes
-- through it.
CREATE INDEX topics_module_idx ON topics (module, sort_order);
