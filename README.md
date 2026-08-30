# Java Quiz — backend

Quarkus REST API + PostgreSQL for the Java Quiz web app.

**Stack:** Java 17, Quarkus 3.34, Jakarta REST, Hibernate ORM, Flyway, PostgreSQL, JWT.

Ported off Spring Boot for start-up time: the free Render instance sleeps after 15 minutes and
pays the boot cost on the next visitor. Same machine, same database, same content migrations
already applied:

| | start-up | resident memory |
|---|---|---|
| Spring Boot 3.3 | 7.2 – 7.9 s | ~420 MB |
| Quarkus 3.34, JVM | **2.13 s** | **~265 MB** |

A cold boot that also runs all six migrations against an empty database takes 3.0 s. The memory
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
| POST | `/api/practice/tasks/{id}/check` | user, parse only |
| POST | `/api/practice/tasks/{id}/run` | user, run and grade |
| GET | `/api/admin/users` | **ADMIN** |
| PATCH | `/api/admin/users/{id}/role` | **ADMIN** |

Default admin after Flyway: `admin@javaquiz.local`, password taken from `ADMIN_INITIAL_PASSWORD`
(`admin123` under `docker compose`). Without that variable the migration generates a random
password and writes it to the log once — a fixed default would be an open door on a public host.

Content is 6 topics, 49 article sections and 294 quiz questions, all bilingual, loaded from
`src/main/resources/content/` by Flyway **Java** migrations — Java rather than SQL scripts
because Spring questions contain `${...}` placeholders that Flyway would interpolate.

| Migration | Loads | From |
|---|---|---|
| `V2__LoadContent` | Java Core, Spring, Spring Boot, Hibernate, Kafka | `content/topics.json`, `content/materials/`, `content/questions/` |
| `V5__LoadPractice` | SQL practice datasets and exercises | `content/practice/sql.json` |
| `V6__LoadSqlTopic` | the SQL topic: sections, articles, quiz questions | `content/sql/` |

SQL lives in its own directory rather than in the shared files because V2 has already run
everywhere; adding a topic to `topics.json` would load it on a fresh database and skip it on
an existing one.

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
