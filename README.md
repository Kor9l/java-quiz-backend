# Java Quiz — backend

Quarkus REST API + PostgreSQL for the Java Quiz web app.

**Stack:** Java 17, Quarkus 3.34, Jakarta REST, Hibernate ORM, Flyway, PostgreSQL, JWT.

Two modules sit behind the sign-in and the learner picks one: **Backend**, the Java / Spring / SQL
material, quiz and exercises this started as, and **English**, the vocabulary trainer merged in
from [english-words-learning-app](https://github.com/Kor9l/english-words-learning-app).
`GET /api/modules` is what that choice reads; the English half is described under
[English](#english).

Ported off Spring Boot for start-up time: the free Render instance sleeps after 15 minutes and
pays the boot cost on the next visitor. Same machine, same database, same content migrations
already applied:

| | start-up | resident memory |
|---|---|---|
| Spring Boot 3.3 | 7.2 – 7.9 s | ~420 MB |
| Quarkus 3.34, JVM | **2.13 s** | **~265 MB** |

A cold boot that also runs every migration against an empty database takes 3.0 s. The memory
halving matters as much as the seconds: the free tier caps the container at 512 MB.

Nothing about the HTTP contract moved — same paths, same JSON, same JWTs, same Google redirect
URIs — so the frontend needed no change. Two behaviours did change on purpose, both listed under
[Notes on the port](#notes-on-the-port).

## API

| Method | Path | Access |
|---|---|---|
| POST | `/api/auth/register` | public, email + password |
| POST | `/api/auth/login` | public |
| GET | `/api/auth/providers` | public, which sign-in methods are enabled |
| GET | `/oauth2/authorization/google` | public, starts Google sign-in |
| GET | `/login/oauth2/code/google` | public, Google callback |
| GET | `/api/auth/me` | user |
| GET | `/api/topics` | user, with personal read-state |
| GET | `/api/materials/{topicId}/{sectionId}` | user |
| POST | `/api/quiz/start` | user |
| GET | `/api/quiz/current` | user |
| POST | `/api/quiz/{id}/reveal\|answer\|advance\|quit` | user |
| GET/PUT | `/api/settings` | user |
| GET | `/api/stats` | user (personal only) |
| POST | `/api/stats/reset` | user |
| POST | `/api/progress/{topic}/{section}/read\|unread` | user |
| POST | `/api/progress/reset` | user |
| GET | `/api/practice` | user, tracks with progress |
| GET | `/api/practice/tracks/{track}` | user, difficulties with progress |
| GET | `/api/practice/tracks/{track}/{difficulty}` | user, task list |
| GET | `/api/practice/tasks/{id}` | user, statement, dataset schema, expected result |
| POST | `/api/practice/tasks/{id}/check` | user, parse (SQL) or compile (Java), without running |
| POST | `/api/practice/tasks/{id}/run` | user, run and grade |
| GET | `/api/modules` | user, the post-login choice with per-module counts |
| GET | `/api/english/groups` | user, groups they may see, with word counts |
| GET | `/api/english/words` | user, the whole vocabulary, already grouped |
| GET | `/api/english/groups/{id}` | user, one group with its words |
| POST | `/api/english/groups` | user, creates a personal group |
| PATCH/DELETE | `/api/english/groups/{id}` | owner, or **ADMIN** on a shared group |
| POST | `/api/english/groups/{id}/words` | owner, or **ADMIN** on a shared group |
| PUT/DELETE | `/api/english/words/{id}` | owner, or **ADMIN** on a shared group |
| POST | `/api/english/words/import` | user, bulk add from pasted text or typed rows |
| POST | `/api/english/words/{id}/favorite` | user, toggles, personal |
| GET | `/api/admin/users` | **ADMIN** |
| PATCH | `/api/admin/users/{id}/role` | **ADMIN** |

Default admin after Flyway: `admin@javaquiz.local`, password taken from `ADMIN_INITIAL_PASSWORD`
(`admin123` under `docker compose`). Without that variable the migration generates a random
password and writes it to the log once — a fixed default would be an open door on a public host.

Content is 7 topics, 61 article sections and 366 quiz questions, all bilingual, loaded from
`src/main/resources/content/` by Flyway **Java** migrations — Java rather than SQL scripts
because Spring questions contain `${...}` placeholders that Flyway would interpolate.

| Migration | Loads | From |
|---|---|---|
| `V2__LoadContent` | Java Core, Spring, Spring Boot, Hibernate, Kafka | `content/topics.json`, `content/materials/`, `content/questions/` |
| `V5__LoadPractice` | SQL practice datasets and exercises | `content/practice/sql.json` |
| `V6__LoadSqlTopic` | the SQL topic: sections, articles, quiz questions | `content/sql/` |
| `V7__levels` | the `level` column on questions and sections | — |
| `V8__LoadJavaConcurrencyTopic` | the Java Concurrency topic: sections, articles, quiz questions | `content/java-concurrency/` |
| `V10__LoadWords` | the English vocabulary: 8 groups, 462 words | `content/english/words.json` |
| `V13__LoadJavaPractice` | Java practice exercises | `content/practice/java.json` |
| `V14__LoadWords2026Part2` | one more English group: "2026 part 2 words", 42 words | `content/english/words-2026-part-2.json` |

SQL and Java Concurrency live in their own directories rather than in the shared files because
V2 has already run everywhere; adding a topic to `topics.json` would load it on a fresh database
and skip it on an existing one. Each new topic therefore ships as `content/<topic>/` plus its own
Java migration, guarded by a content test that runs at build time.

## Levels

Every question and section carries a career `level` — `JUNIOR`, `MIDDLE` or `SENIOR` — next to
the `difficulty` a question already had. The two are orthogonal: difficulty says how tricky a
question is, level says who is expected to know the material at all. Everything loaded before
V7 was written for a middle-level reader and is tagged `MIDDLE`.

Levels are cumulative. A track draws on its own level and every level below it, so
fundamentals stay in the senior pool instead of vanishing from it, and `QuestionPicker` then
halves the weight of each level below the track — same level ×1, one below ×0.5, two below
×0.25. A senior session therefore leans senior while still revisiting basics, and a junior
session is never diluted because nothing sits below it.

The track comes from `level` in `/api/settings`, and `POST /api/quiz/start` accepts a `level`
of its own to override it for a single session. Sections report their level in `/api/topics`
and `/api/materials/...` but are never filtered out of them: labelling a section that is above
the reader is useful, hiding it would also hide progress they already have on it.

## Java Concurrency

Twelve sections rather than the usual eight, because the path from "what is a thread" to
structured concurrency does not compress without losing the junior entry point — and that entry
point is what makes the topic usable by all three tracks. Reading order never drops back to an
easier level: three junior sections, six middle, three senior.

72 questions, spread 21 junior / 31 middle / 20 senior so that no track draws on an empty pool.
Section level is the level of the *article*; a question inside it may sit at a different level,
which is the point of keeping the two axes separate.

## SQL

SQL is a topic like the others — 8 sections of study material and 48 quiz questions — plus a
practice track, and the three are cross-linked: an article offers the exercises that drill it,
and every exercise links back to the section it belongs to.

### Practice

Exercises the learner solves by writing a query that is then executed, rather than by picking
an option. 54 tasks over three datasets (a shop, a staff directory and a lending library),
split into easy / medium / hard.

**Grading is by result, not by text.** Every task ships a reference solution; a submission is
correct when its result set matches what the reference produces. Column labels are ignored,
row order only counts when the task asked for an order, and numeric values are compared as
decimals so `COUNT(*)` and `SUM(1)` agree. Two learners can arrive through a join, a subquery
or a window function and both pass.

A submission runs in a **throwaway in-memory H2 database** (`MODE=PostgreSQL`), built from the
dataset's DDL and dropped when the attempt ends. Two rings keep it harmless:

1. `SqlGuard` rejects anything that is not a single read-only statement, before execution.
2. The statement runs as a database user holding nothing but `SELECT` grants, so `INSERT`,
   `DROP`, `CREATE ALIAS`, `FILE_READ` and `CSVREAD` are refused by the engine itself.

Plus a query timeout, a row cap and a length cap — all under `app.practice` in
`application.yml`, overridable with `PRACTICE_*` environment variables.

`SqlPracticeContentTest` runs every bundled reference solution at build time, so a task whose
SQL no longer works fails the build rather than a learner's session. `SqlTopicContentTest`
does the same for the articles and questions V6 loads, since that migration only gets to fail
once, against a real database.

The second practice track is [Java](#java-practice), which grades the same way and sits behind
the same two endpoints.

## Java practice

The same idea carried into a compiled language: 18 exercises the learner solves by writing a
class that is then **compiled and run**, six each at easy / medium / hard, drawn from seven Java
Core sections and cross-linked with them the way the SQL ones are.

Where a SQL task is a dataset plus a reference query, a Java task is a class plus the **cases**
called against it — each case a Java expression like `Solution.reverse("java")`. The cases are
shown to the learner rather than hidden: they are the specification.

**Grading is by result, not by text**, exactly as on the SQL track. The reference solution is
compiled and run first, its answer for each case is what a submission is compared against, and a
loop, a stream and a recursion that all return the same values are all correct. The comparison
reuses `ResultComparator`: cases become rows, so a wrong answer comes back pointing at the first
case that differs.

`POST /check` compiles without running, which is the whole answer for a learner who only wants
to know whether it builds. `POST /run` compiles, runs and grades. Both return `diagnostics` —
severity, line, column, message — and `run` also returns `output`, one entry per case, holding
whatever the submission printed.

### Three rings

Submitted code is arbitrary code, and the sandbox is `SandboxPolicy`: an allowlist of nine
`java.*` packages, minus the classes inside them that are refused anyway, plus the members that
are allowed on a class too useful to refuse whole. `System.out` is that case — `System.exit` is
why it cannot simply be allowed. Threads are refused outright: a thread a submission starts
outlives the attempt that started it.

The policy is enforced three times, each ring covering what the one outside it can miss.

1. `JavaGuard` reads the source: length, a package declaration, an import of a refused package.
   Cheap, and there for the message rather than the protection.
2. `ClassFileGuard` walks the **compiled constant pool**, which is where a class writes down
   every other class it names and every member it calls. A fully-qualified name, a string trick
   or a comment all collapse to the same entries by the time the compiler is finished, so this
   catches what reading the source cannot.
3. `SandboxClassLoader` re-checks every class as it is actually resolved, with the platform
   loader as its parent rather than the application's. A reference the parser misread ends as a
   `ClassNotFoundException` instead of as a loaded class — and the application's own classes are
   not reachable at all.

A loader per attempt, thrown away with it: a static field a submission sets does not outlive the
submission that set it.

### What the timeout can and cannot do

A run that overruns is interrupted, and interruption is cooperative — a submission spinning in a
tight loop that never blocks does not observe it. Java has no safe way to stop such a thread, so
the sandbox abandons it as a daemon and counts it; after four the track reports itself busy
rather than filling the process with spinning threads. Heap is the gap this leaves: a submission
that allocates until the JVM gives up is caught as a runtime error, not prevented.

`System.out` is captured by a stream installed on first use that routes **by thread** — an
attempt's writes go to that attempt's buffer, every other thread's to the stream that was there
before. Swapping `System.out` per attempt was the alternative, and it would mean either
serialising every submission or handing one learner another's output. Installation is lazy
rather than at boot, which is after the log manager has taken its own reference, so application
logging never goes through it.

### The compiler

ECJ, bundled as a dependency and loaded through the `javax.tools` service interface, rather than
the JDK's own. The runtime image is `eclipse-temurin:17-jre`, and on a JRE
`ToolProvider.getSystemJavaCompiler()` returns null — there is no `jdk.compiler` module to find,
and switching to a JDK image is the larger change for 3 MB. It also means the error message a
learner reads is the one the build tested against, on every machine this runs on.

Compilation is in memory, with an **empty class path**: submitted code sees the JVM's own
`java.*` and nothing else. The submission is compiled as itself rather than wrapped in
scaffolding, so line 7 of a diagnostic is line 7 of the learner's editor. The generated harness
holding the cases is a separate unit, and an error in *it* means something else — the submission
compiled, but does not have the shape the cases call for — which is reported under its own
message key, without a line number the learner has no way to look at.

Limits live under `app.practice.java` in `application.properties`, overridable with
`PRACTICE_JAVA_*` environment variables: a run timeout covering all of a task's cases together,
a source length cap and an output cap.

`JavaPracticeContentTest` compiles and runs every bundled reference solution at build time and
grades it against its own cases, so a task whose solution or cases have drifted fails the build
rather than a learner's session. It also asserts that no starter already passes. A starter is
allowed not to compile — the wildcard exercise starts from a signature too narrow for its own
cases, and that is the lesson — but it may never be the answer.

## English

A vocabulary trainer, merged in from the standalone
[english-words-learning-app](https://github.com/Kor9l/english-words-learning-app). That app was
Spring Boot with Thymeleaf pages; what came across is the data and the word management, rewritten
as REST against the schema and conventions already here. Its learning quiz, statistics screens and
its own user table stayed behind — this app already has accounts, and the drilling loop was not
part of the move.

It is a module rather than a topic: no sections, no levels, no `topics.json` entry, and none of
its tables touch the ones above. `/api/modules` is the only place the two meet.

### The words

462 words in 8 groups, in `content/english/words.json` and loaded by `V10__LoadWords`. Two sources
went into that file: the 234-word seed the old app shipped as `data/words.json`, and the 228 rows
its live database had accumulated since — the two overlap by ten words and are otherwise separate.
No group repeats a word, and `EnglishWordsContentTest` keeps it that way; the same word in two
groups was left alone, since a word belonging to two lessons is deliberate. `part_of_speech` and
`difficulty` came over empty in every single row and were not carried into the schema.

A ninth group, `2026 part 2 words`, ships separately as `content/english/words-2026-part-2.json`
and is loaded by `V14__LoadWords2026Part2` — 42 phrases condensed from a lesson handout, with the
phrase itself as the entry, the sentence it was shown in kept as the example where it helps, and
the grammar notes left behind. It is a file of its own for the same reason a new topic is: V10 has
already run everywhere, so a group appended to `words.json` would load on a fresh database and be
missing on an existing one.

**Every seeded group is PUBLIC.** Four of the eight were one learner's private groups in the old
app, but that app numbered its users and this one identifies them by UUID, so there is nobody here
to hand them back to. Shipping them as content is what keeps them reachable; anything a learner
adds from now on is PERSONAL and theirs alone.

### Groups and who may touch them

One distinction carries the whole module. A group is either PUBLIC — shipped, or curated by an
admin — or PERSONAL, created by one learner from their own text.

| | reads | edits |
|---|---|---|
| PUBLIC | everybody | admins |
| PERSONAL | its owner | its owner |

A group the caller may not read answers **404, not 403**: telling an outsider that someone else's
group exists is already more than they should learn. A group they may read but not write answers
403.

Favourites are the exception to the shared/private split — they hang off `(user_id, word_id)`, so
one learner stars a word in a shared group without the others seeing it. The `correct_count` and
`incorrect_count` on a word are the opposite: they came over from the old app as single global
numbers, and nothing writes to them here, since the drilling loop that would is not part of this
move.

### Bulk import

`POST /api/english/words/import` takes either a pasted list or typed rows, into an existing group
or a new personal one named on the spot:

```json
{"mode": "TEXT", "newGroupTitle": "Idioms 9", "text": "1 to go global — выйти на мировой уровень"}
{"mode": "TABLE", "groupId": "…", "rows": [{"text": "compassion", "translation": "сострадание"}]}
```

The text format is the old app's, unchanged: one word per line, English and translation split by
an **em or en dash** — a plain hyphen is not a separator, because too many entries contain one. A
leading number is a list marker and is dropped, and a `*` after it flags a word as newly met:

```
1 definitive — окончательный
5 * to fade away — угасать
ongoing — текущий
```

The number may be decimal (`1.2 word`) but must not be followed by a bare dot: `1. word` keeps the
`1.` as part of the English side. That is how the app this came from behaved, and it was left that
way rather than quietly changed during a port.

A line that will not parse is **reported and skipped**, not fatal — the usual paste has a stray
line or two in it, and re-pasting the other forty is not a fix. The response says how many landed
and names the rest by line number:

```json
{"groupId": "…", "groupTitle": "Idioms 9", "imported": 3,
 "errors": [{"line": 4, "code": "MISSING_SEPARATOR"}]}
```

| code | the line had |
|---|---|
| `MISSING_SEPARATOR` | no em or en dash, so there is no telling where the English stops |
| `EMPTY_SIDE` | a dash with nothing on one side of it |
| `MISSING_FIELDS` | (typed rows) neither a word nor its translation |

A code rather than a sentence, unlike the validation messages under
[Notes on the port](#notes-on-the-port). Those are read by whoever is calling the endpoint; these
reach a learner mid-paste, on a screen the UI has already translated around them — and only the UI
knows which language that is, so the wording lives there and the reason travels as a name. Adding
a code is a UI change too: an unknown one renders as the bare code rather than as nothing.

A run that imports nothing and reports nothing means the payload was empty, and answers 400 rather
than a cheerful `imported: 0`; if it was about to create a group, the transaction takes that back
with it.

`WordLineParserTest` pins the accepted shapes and `EnglishWordsContentTest` guards the bundled
corpus, on the same reasoning as the other content tests: `V10` only gets one attempt against a
real database.

## Local run (without Docker)

PostgreSQL must be up (`javaquiz` / `javaquiz` / `javaquiz` on `:5432`).

```bash
mvn quarkus:dev
```

Dev mode reloads on the next request after a source change, so most edits need no restart. To run
what production runs instead:

```bash
mvn package && java -jar target/quarkus-app/quarkus-run.jar
```

`quarkus-app/` is a directory, not an uber-jar — `quarkus-run.jar` needs `lib/`, `app/` and
`quarkus/` beside it, which is why the Dockerfile copies all four.

## Docker

This repo owns `docker-compose.yml` for **three** containers: Postgres, backend, frontend.

The frontend project must sit next to this folder as `../java-quiz-frontend`.

Windows:

```powershell
.\start.ps1
```

Linux / macOS:

```bash
chmod +x start.sh && ./start.sh
```

Open http://localhost — nginx serves the UI and proxies `/api` to the backend.

```
docker compose down        # stop
docker compose down -v     # stop and wipe the database volume
```

## Google Sign-In

Put the credentials from the Google console in `.env` next to `docker-compose.yml` (gitignored):

```
GOOGLE_CLIENT_ID=...apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=...
```

Authorised redirect URI in the Google console must be exactly:

```
http://localhost:8080/login/oauth2/code/google
```

A deployed instance needs its own entry there as well —
`https://<service>.onrender.com/login/oauth2/code/google`.

The flow is the standard authorization code grant: the UI sends the browser to
`/oauth2/authorization/google`, Google calls the backend back, and the backend issues the app JWT
and redirects to `APP_FRONTEND_URL/auth/callback#token=…`. An account with a matching email is
linked rather than duplicated, so password login keeps working. Without credentials the button
stays disabled and `/api/auth/providers` reports Google as unavailable.

Extra environment variables: `APP_FRONTEND_URL` (default `http://localhost`) and `APP_PUBLIC_URL`
(default `http://localhost:8080`) — the latter must match the host in the redirect URI.

If a local PostgreSQL already owns port 5432, set `POSTGRES_PORT` in `.env` (e.g. `POSTGRES_PORT=15432`);
only the host port changes, the backend still reaches the container on 5432.

## Deploy

Free tier in three parts: Postgres on Neon, this service on Render, the UI on Render too, as a
static site (see the frontend repo). The database sits on Neon because Render deletes its own
free Postgres after 30 days.

The UI moved off Cloudflare Pages because `*.pages.dev` is reported as filtered by some ISPs in
Belarus and Russia. Only the origin changed — the two hosts still talk cross-origin, exactly as before, so
`CORS_ORIGINS` and `APP_FRONTEND_URL` below now carry an `onrender.com` address instead of a
`pages.dev` one.

1. **Neon** — create a project and a `javaquiz` database. Region `eu-central-1` keeps it next to
   Render's Frankfurt.
2. **Render** — *New → Blueprint*, pointed at this repo. [`render.yaml`](render.yaml) declares the
   service; the dashboard asks for the values it marks `sync: false`:

   | Variable | Value |
   |---|---|
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<neon-host>/javaquiz?sslmode=require` |
   | `SPRING_DATASOURCE_USERNAME` | Neon user |
   | `SPRING_DATASOURCE_PASSWORD` | Neon password |
   | `ADMIN_INITIAL_PASSWORD` | password for the seeded admin |
   | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | optional, see [Google Sign-In](#google-sign-in) |

   `JWT_SECRET` is generated by Render itself.

   The three URLs — `CORS_ORIGINS`, `APP_FRONTEND_URL`, `APP_PUBLIC_URL` — are *not* in that list.
   They carry values the browser already sees, so the blueprint holds them literally and the
   dashboard never asks. If either service is renamed, they change in [`render.yaml`](render.yaml)
   and nowhere else. Getting `CORS_ORIGINS` wrong is unusually quiet: Quarkus' CORS filter answers
   an unlisted origin with a bare `403` and no body, the browser reports it as a failed request,
   and neither service logs which origin was refused. `curl` the same path *without* an `Origin`
   header — a `200` there and a `403` with one is that mismatch and nothing else. The value must
   match the browser's origin exactly, with no trailing slash.

   The three database variables keep their Spring-era names deliberately. They are `sync: false`,
   so renaming a key in the blueprint does not carry its value across: the next sync would create
   the new key empty, and an empty key is not an absent one — no default or fallback in the app
   covers it, and the service would fail to boot. Renaming them is a dashboard operation, done
   first, not a side effect of a code change.

   Neon's free tier scales to zero, so the first connection of a boot can take several seconds.
   `quarkus.datasource.jdbc.acquisition-timeout` and `quarkus.flyway.connect-retries` are set for
   that; the defaults (5 s, no retry) are tighter than the 30 s HikariCP allowed under Spring, and
   the first deploy of the Quarkus port died on exactly that.
3. **GitHub** — *Settings → Secrets and variables → Actions*, add the secret
   `RENDER_DEPLOY_HOOK` (Render → service → *Settings → Deploy Hook*).

Every push to `master` then runs [the workflow](.github/workflows/deploy.yml): `mvn verify` first,
and the deploy hook only on success. Render's own auto-deploy is disabled in `render.yaml`, so a
red build never reaches production.

Both free tiers idle down: the Render instance sleeps after 15 minutes, and Neon wakes in a second
or two. Waking Render is mostly Render's own container scheduling; the application's share of it
is the ~2 s above rather than the ~8 s it used to be.

## Notes on the port

Two behaviours deliberately differ from the Spring build.

**Missing or invalid token answers 401, not 403.** Spring's chain had no authentication entry
point configured, so an unauthenticated request fell through to `Http403ForbiddenEntryPoint`.
The UI has always watched for 401 to drop an expired token and send the user back to sign in
(`src/api.js`), so that branch never actually fired. It does now. 403 is left to what it means:
authenticated, but not allowed — an ordinary user reaching `/api/admin/**`.

**Validation messages are fixed English strings.** They used to come from Hibernate Validator's
bundle, resolved against a locale, so the same rejected password read differently on a developer's
machine and on the server. The constraints in `api/dto/` now spell their messages out. The
response shape is unchanged: `{"message": "..."}`, which is what the UI reads.

Worth knowing while working in here:

- **Repositories are plain JPQL** over an injected `EntityManager` (`domain/*Repository.java`)
  rather than Spring Data interfaces. Method names are the ones the services already called, so
  the query each one used to generate is now written down. `save` is `EntityManager#merge`, which
  is what Spring Data did for these entities — they all assign their own id.
- **Authentication is a `HttpAuthenticationMechanism`** plus an `IdentityProvider`
  (`security/Jwt*.java`), and the role still comes from the database rather than the token's
  `role` claim: a token lives a day, so trusting the claim would let a demoted admin stay an admin.
- **`/api` is closed by default** through `quarkus.http.auth.permission.*` in
  `application.properties`, the same line the Spring filter chain drew. A new endpoint under
  `/api` is authenticated until it is listed as public — not the other way round.
- **`jsonb` columns have their own Jackson mapper** (`config/DatabaseJsonFormatMapper.java`).
  Quarkus refuses to share the REST mapper with the database, and it is right to: retuning that
  mapper for an endpoint would quietly rewrite stored rows. Its settings match what the Spring
  build wrote those rows with.
- **Google sign-in is written out** in `api/GoogleOAuthResource.java` instead of delegating to an
  OIDC extension — two endpoints and one form POST, at the URLs the Google console already knows.
  The CSRF `state` lives in a five-minute HttpOnly cookie, so unlike the Spring version the
  service keeps no session at all.
- **Flyway's Java migrations are constructed by Flyway, not by CDI**, so `@Inject` in one stays
  null. `V3__SeedAdmin` calls `PasswordHasher` statically for that reason.

Further gains are available if the cold start ever matters more than it does now: an AppCDS
archive (`quarkus.package.jar.appcds.enabled`) takes roughly a third off the 2 s, and a GraalVM
native build takes it to tens of milliseconds — at the cost of a build heavy enough that Render's
free builder may not have the memory for it.
