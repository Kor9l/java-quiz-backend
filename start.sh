#!/usr/bin/env bash
# Java Quiz — Docker Compose launcher
# Run from the backend folder. Requires Docker and sibling ../java-quiz-frontend.

set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f "../java-quiz-frontend/package.json" ]; then
  echo "ERROR: ../java-quiz-frontend not found. Clone/create the frontend next to this folder."
  exit 1
fi

cat <<'EOF'

Java Quiz — starting 3 containers
  postgres  :5432
  backend   :8080  (API /api)
  frontend  :80    (UI, proxies /api to backend)

Default admin:  admin@javaquiz.local / admin123
Register a regular user on the login page.
Google Sign-In is a stub until GOOGLE_CLIENT_ID is set and verification is implemented.

First start builds images and runs Flyway (topics, 246 questions, 41 articles).
Stop: docker compose down     Data: docker compose down -v

EOF

docker compose up --build "$@"
