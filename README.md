# English Prep

English learning app, starting with TOEIC (IELTS later). Runs on desktop web, iOS and Android.

**Current version (v1):** vocabulary management + spaced repetition (SM-2).

## Tech stack

| Part | Technology |
|---|---|
| Backend | Java 21, Spring Boot, Gradle (Kotlin DSL), PostgreSQL, Flyway |
| Client | Expo (React Native), TypeScript |
| Infrastructure | Docker Compose (development) |

## Repository layout

```
apps/api/    Spring Boot backend
apps/app/    Expo client
infra/       Docker Compose and deployment config
docs/        Project plan and progress log
```

## Requirements

- Java 21
- Node 24 LTS (pinned in `.tool-versions`, managed with asdf) and pnpm 10
- Docker Desktop

## Documentation

- [Project plan](docs/PROJECT_PLAN.md): goals, stack, architecture, roadmap, decisions
- [Progress log](docs/PROGRESS.md): every step taken, in order
