# Java Quiz — Docker Compose launcher (Windows)
# Run from the backend folder. Requires Docker Desktop and the sibling ../java-quiz-frontend project.

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path "..\java-quiz-frontend\package.json")) {
    Write-Host "ERROR: ../java-quiz-frontend not found. Clone/create the frontend next to this folder."
    exit 1
}

Write-Host ""
Write-Host "Java Quiz — starting 3 containers"
Write-Host "  postgres  :5432"
Write-Host "  backend   :8080  (API /api)"
Write-Host "  frontend  :80    (UI, proxies /api to backend)"
Write-Host ""
Write-Host "Default admin:  admin@javaquiz.local / admin123"
Write-Host "Register a regular user on the login page."
Write-Host "Google Sign-In is a stub until GOOGLE_CLIENT_ID is set and verification is implemented."
Write-Host ""
Write-Host "First start builds images and runs Flyway (topics, 246 questions, 41 articles)."
Write-Host "Stop: docker compose down     Data: docker compose down -v"
Write-Host ""

docker compose up --build @args
