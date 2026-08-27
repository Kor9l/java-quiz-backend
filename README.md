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
| GET | `/api/admin/users` | **ADMIN** |
| PATCH | `/api/admin/users/{id}/role` | **ADMIN** |

Default admin after Flyway: `admin@javaquiz.local` / `admin123`.

Content (5 topics, 246 questions, 41 articles) is loaded by Flyway Java migration `V2__LoadContent` from `src/main/resources/content/`. Java is used because Spring questions contain `${...}` placeholders that SQL scripts would interpolate.

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

The flow is the standard authorization code grant: the UI sends the browser to
`/oauth2/authorization/google`, Google calls the backend back, and the backend issues the app JWT
and redirects to `APP_FRONTEND_URL/auth/callback#token=…`. An account with a matching email is
linked rather than duplicated, so password login keeps working. Without credentials the button
stays disabled and `/api/auth/providers` reports Google as unavailable.

Extra environment variables: `APP_FRONTEND_URL` (default `http://localhost`) and `APP_PUBLIC_URL`
(default `http://localhost:8080`) — the latter must match the host in the redirect URI.

If a local PostgreSQL already owns port 5432, set `POSTGRES_PORT` in `.env` (e.g. `POSTGRES_PORT=15432`);
only the host port changes, the backend still reaches the container on 5432.
