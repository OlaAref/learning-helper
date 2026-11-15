# Learning Helper

One app for two learning tools, behind one UI:

- **Notion Sync** — two-way mirror between your Notion workspace and local Markdown files.
- **DB Practice Lab** — quizzes and hands-on PostgreSQL tasks for Fundamentals of Database Engineering.

```
Browser ──► gateway :8080 ──┬── /api/notion/**  ──► notion-service :8081
            (serves the UI) └── /api/db/**      ──► db-lab-service :8082
```

Design and decisions: [ARCHITECTURE.md](ARCHITECTURE.md).

## Requirements

Java 21+, Maven, Node 22+, Docker (for the lab PostgreSQL servers).

## Build

```powershell
cd ui; npm install; npm run build; cd ..   # UI -> gateway/src/main/resources/static
mvn -q package                             # three jars; add -DskipTests to skip the Docker-backed lab tests
```

## Run

```powershell
docker compose up -d                 # PostgreSQL on 5433 (labs) and 5434 (crash tasks)
$env:NOTION_TOKEN = "secret_xxx"     # or have it set as a Windows variable
mvn package -DskipTests
.\run-all.ps1                        # notion-service, db-lab-service, gateway
```

Open http://localhost:8080.

To run one service alone, start it from its own folder (the relative paths in its config depend on that):

```powershell
cd services\db-lab-service; mvn spring-boot:run
```

The home screen shows which services are running. Each project works without the other.

## Develop the UI

```powershell
cd ui; npm run dev     # http://localhost:5173, proxies /api to the gateway on 8080
npm test
```

## Layout

| Path | What |
|---|---|
| `gateway/` | Spring Cloud Gateway (WebMVC): routes, local-only guard, `/api/services` status |
| `services/notion-service/` | Notion mirror (was `notion-mirror`) |
| `services/db-lab-service/` | Practice lab (was `db-practice-lab`) |
| `ui/` | The single React app: `home/`, `notion/`, `dblab/`, `shared/` |
| `content/` | Quiz and task YAML for the lab |
| `data/` | Runtime data, git-ignored: `notion-pages/`, `db-lab/progress.yml` |

## Notes

- `data/notion-pages` was copied from `D:\Project\notion-mirror\pages` with its sync state. Sync only from this app now.
- `compose.yaml` uses the container names `dblab-pg` and `dblab-crash`. If the old db-practice-lab containers exist,
  remove them first (`docker rm -f dblab-pg dblab-crash`) or keep using them as they are.
- Settings live in each service's `src/main/resources/application.yml`; service addresses for the gateway are in
  `gateway/src/main/resources/application.yml` under `services`.
