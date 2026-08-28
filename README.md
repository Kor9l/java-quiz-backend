# Java Quiz — backend

Spring Boot REST API + PostgreSQL for the Java Quiz web app.

**Stack:** Java 17, Spring Boot 3.3, Spring Security (JWT), JPA, Flyway, PostgreSQL.

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
mvn spring-boot:run
```

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

Free tier in three parts: Postgres on Neon, this service on Render, the UI on Cloudflare Pages
(see the frontend repo). The database sits on Neon because Render deletes its own free Postgres
after 30 days.

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
   | `APP_PUBLIC_URL` | `https://<service>.onrender.com` |
   | `APP_FRONTEND_URL` | `https://<project>.pages.dev` |
   | `CORS_ORIGINS` | `https://<project>.pages.dev` |
   | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | optional, see [Google Sign-In](#google-sign-in) |

   `JWT_SECRET` is generated by Render itself.
3. **GitHub** — *Settings → Secrets and variables → Actions*, add the secret
   `RENDER_DEPLOY_HOOK` (Render → service → *Settings → Deploy Hook*).

Every push to `master` then runs [the workflow](.github/workflows/deploy.yml): `mvn verify` first,
and the deploy hook only on success. Render's own auto-deploy is disabled in `render.yaml`, so a
red build never reaches production.

Both free tiers idle down: the Render instance sleeps after 15 minutes and needs ~40–60 s to boot
the JVM again, Neon wakes in a second or two.
