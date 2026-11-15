# Learning Helper — architecture

Status: **approved and implemented**.

Learning Helper merges two existing apps into one product with one UI:

| Existing project | Becomes | What it does |
|---|---|---|
| `D:\Project\notion-mirror` | `notion-service` | Two-way mirror of Notion pages to local Markdown files |
| `D:\Project\db-practice-lab` | `db-lab-service` | Quizzes and hands-on PostgreSQL tasks for the database course |

The two original folders are left untouched. Code and data are copied, not moved.

## 1. Overview

```
Browser ──► gateway :8080 ──┬── /api/notion/**  ──► notion-service :8081 ──► Notion API, data/notion-pages
            (serves the UI) └── /api/db/**      ──► db-lab-service :8082 ──► PostgreSQL :5433 / :5434, content/
```

- **One UI** (React), built once and served by the gateway. It only ever talks to the gateway.
- **One microservice per project.** Each owns its own data and can be started, stopped and tested alone.
- **Gateway** is the single entry point: static UI, routing, and the local-only guard.
- Services never call each other. The only link between the two domains is in the UI (the home screen).

## 2. Folder layout

```
learning-helper/
├── pom.xml                      parent POM (Spring Boot 4.1.2, Java 21), lists the three modules
├── gateway/                     Spring Cloud Gateway Server (WebMVC); UI build lands in its static/ folder
├── services/
│   ├── notion-service/          code from notion-mirror, package com.olaaref.notionmirror kept
│   └── db-lab-service/          code from db-practice-lab, package com.olaaref.dbpracticelab kept
├── ui/                          the single React + Vite app
├── content/                     quiz and task YAML (from db-practice-lab/content)
├── data/                        git-ignored runtime data
│   ├── notion-pages/            copy of notion-mirror/pages, including .notion-sync state
│   └── db-lab/progress.yml
├── compose.yaml                 the two lab PostgreSQL servers
├── run-all.ps1                  starts the three Java processes
└── README.md
```

Java packages keep their current names so the moved code and its tests need no edits beyond configuration.

## 3. Services

### gateway (port 8080)

- `spring-cloud-starter-gateway-server-webmvc` (Spring Cloud 2025.1.x, the train that supports Boot 4.1).
- Routes, with the prefix removed before forwarding so the services keep their current `/api/...` paths:

  | UI calls | Forwarded to |
  |---|---|
  | `/api/notion/**` | `http://127.0.0.1:8081/api/**` |
  | `/api/db/**` | `http://127.0.0.1:8082/api/**` |

- Service addresses are plain properties (`services.notion.url`, `services.db-lab.url`). No service registry: everything runs on one machine.
- Serves the built UI from `/`.
- `LocalOnlyFilter` (Host/Origin must be localhost) moves here and stays in each service as a second layer.
- `GET /api/services` returns up/down for each service so the home screen can show which project is available.
- Must pass through: server-sent events (`/api/notion/events`) without buffering, and image uploads up to 20 MB.

### notion-service (port 8081)

- Same code and endpoints as notion-mirror: status, page tree, page read/save/create, image upload, assets, sync, events.
- Config changes only: port 8081, `sync.local-folder` → `D:/Project/learning-helper/data/notion-pages`.
- `NOTION_TOKEN` still comes from the environment and is never written to disk.

### db-lab-service (port 8082)

- Same code and endpoints as db-practice-lab: overview, quiz, tasks, labs, sessions, chaos, check.
- Config changes only: port 8082, `lab.content-dir` → `../../content`, `lab.progress-file` → `../../data/db-lab/progress.yml`.
- Adds the actuator health endpoint (notion-service already has it) for the gateway's status check.
- PostgreSQL targets and the `docker kill/start dblab-crash` chaos commands are unchanged.

## 4. UI

One Vite app, organised by feature:

```
ui/src/
├── main.tsx, App.tsx            shell: theme class, top-level route switch
├── router.ts                    one hash router for the whole app
├── shared/                      theme hook, markdown renderer, icons, base CSS tokens
├── home/                        "Choose a project" screen
├── notion/                      api.ts, Sidebar, PageView, NewPageDialog, SettingsPanel, tree
└── dblab/                       api.ts, Sidebar, Home, LecturePage, QuizPlayer, TaskRunner, SessionPanel
```

Routes:

| Hash | Screen |
|---|---|
| `#/` | Home: two project cards (Notion Sync, DB Practice Lab) |
| `#/notion`, `#/notion/page/<id>` | Notion workspace |
| `#/db` | DB lab overview |
| `#/db/lecture/<section>/<lecture>` | Lecture + quiz |
| `#/db/task/<section>/<lecture>/<task>` | Task runner |

Matching the approved design:

- Home screen lists both projects; each card opens its project. A card shows "service not running" when its service is down.
- "All projects" link at the bottom of both sidebars.
- Light and dark mode on every screen. One theme for the whole app, remembered across screens and reloads.
- Both feature areas share one set of colour tokens (the two existing stylesheets are merged; class names that clash are scoped per feature).

Each feature's `api.ts` only changes its base path (`/api/notion`, `/api/db`). A feature's data is fetched only while its screens are open, so a stopped service does not break the other project.

## 5. Data

| Data | Location | Notes |
|---|---|---|
| Notion pages + sync state | `data/notion-pages/` | Copied once from `notion-mirror/pages`. After that, only learning-helper should sync; the old folder is a frozen backup. |
| Quiz and task content | `content/` | Copied from `db-practice-lab/content`. |
| Lab progress | `data/db-lab/progress.yml` | Copied if `db-practice-lab/data/progress.yml` exists. |

Running the old notion-mirror app again would sync the same Notion workspace into a second folder. It is safe for data but pointless; avoid it.

## 6. Build and run

```
docker compose up -d                 # lab PostgreSQL servers
cd ui && npm install && npm run build   # output goes to gateway/src/main/resources/static
mvn -q package                       # all three modules
./run-all.ps1                        # notion-service, db-lab-service, gateway
```

Then open http://localhost:8080. Start from a terminal that has `NOTION_TOKEN` set.

UI development: `npm run dev` in `ui/` proxies `/api` to the gateway on 8080.

`compose.yaml` keeps the container names `dblab-pg` and `dblab-crash`, so containers created by the old db-practice-lab compose file are reused or must be removed first.

## 7. Decisions

| Decision | Choice | Why |
|---|---|---|
| Entry point | API gateway | One URL, no CORS, services stay private on localhost |
| Gateway flavour | Server WebMVC | Same servlet + virtual-thread stack as both services |
| Service discovery | Static URLs | Single machine; a registry adds a fourth process for no gain |
| Java packages | Unchanged | Smallest diff; existing tests keep passing |
| Repo shape | One Maven multi-module repo | One build, but each service is its own runnable jar |
| Notion pages | Copied into `data/notion-pages` | Chosen by Ola |

## 8. Risks to verify during the build

1. Server-sent events and 20 MB multipart uploads through Gateway Server WebMVC.
2. Exact Spring Cloud 2025.1.x version that resolves against Spring Boot 4.1.2.
3. Merged CSS: both apps use the same class names (`sidebar`, `brand`, `row`, `btn`) with small differences.

## 9. Out of scope

Authentication, containerising the Java services, a service registry, and any change to how sync or the labs behave.
