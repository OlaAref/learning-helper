# Content format

```text
content/<section-id>/
  section.yml                      # course, title, notes page, lecture list
  <lecture-id>/
    quiz.yml                       # optional
    task-<nn>-<slug>.yml           # zero or more tasks
```

The app loads everything at startup and refuses to start on a malformed file (the error names the file).
`mvn test` additionally replays every task's `solution` against PostgreSQL and requires all checks to pass.

## section.yml

```yaml
id: acid
course: Fundamentals of Database Engineering
title: ACID
notes: "Fundamentals of Database Engineering / ACID"   # page in notion-mirror, shown as text
lectures:
  - {id: l08-transaction, number: 8, title: "What is a Transaction?"}   # id = folder name
```

## quiz.yml

```yaml
questions:
  - id: q1
    kind: mcq                  # mcq | predict (predict = what does this code return? shows a "Predict" chip)
    prompt: Markdown text
    code: |                    # optional code block
      SELECT 1;
    options: ["first", "second", "third"]
    answer: 1                  # 0-based index; checked on the server, never sent to the browser
    explanation: Shown after answering (Markdown)
```

## task-*.yml

```yaml
id: t1-short-slug
title: Shown in the lists
kind: sql | concurrency | crash        # crash tasks default to the "crash" target and show kill/start buttons
difficulty: easy | medium | hard
sessions: [A, B]                       # default: [A] (sql, crash) or [A, B] (concurrency)
statement: |
  Markdown shown above the terminals. Tell the learner what to do, never the answer.
setup: |                               # SQL run once on the fresh database
  CREATE TABLE t (id int);
hints: ["first hint", "second hint"]   # revealed one at a time
checks: [...]                          # see below, at least one
solution:                              # required for every task (replayed by the test)
  explanation: Why it works
  steps:
    - {session: A, sql: "BEGIN; ..."}
    - {chaos: kill}                    # or start; crash tasks only
```

### Checks

| type | What it verifies | Fields |
| --- | --- | --- |
| `query` | Rows returned by `sql` on a **fresh connection** (committed data only) equal `expect` | `sql`, `expect: [[row], ...]` |
| `result` | What a session's *n*-th result-producing submission returned (its last result set) | `session`, `nth: "1"` or `"last"`, `expect` |
| `error` | Some statement failed with this SQLSTATE (optionally in `session`) | `sqlstate: "40001"` |
| `chaos` | The server was killed at least once | `killed: true` |

Every check has a `description` shown to the learner. Cells are compared as text; SQL `NULL` is `NULL`.
To prove "no transaction left open", end a task with:

```yaml
- type: query
  description: No transaction is left open
  sql: SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND state LIKE 'idle in transaction%'
  expect: [[0]]
```

## YAML pitfalls (the loader is YAML 1.1)

- Quote `"on"`, `"off"`, `"yes"`, `"no"`, `"true"`, `"false"` when they are values: unquoted they become booleans.
- A plain scalar cannot contain `: ` (colon and space) or ` #`; quote it or use a `|` block.
- In flow mappings like `{id: x, title: "..."}` quote any title with `?`, `:` or `,`.
- Quote `sqlstate` and `nth` values so they stay strings.

## Adding a section

Copy a section folder, change the ids, write the quizzes and tasks, then run `mvn test` (with docker compose up)
to prove every reference solution passes and no check passes by accident.
