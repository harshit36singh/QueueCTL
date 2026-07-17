# queuectl

A CLI-based background job queue system: enqueue shell commands as jobs, run them with one or
more worker threads, retry failures with exponential backoff, and move permanently-failing jobs
to a dead letter queue (DLQ). Built with **Java 17, Spring Boot, and MySQL**.

## Contents

- [Tech stack](#tech-stack)
- [Setup instructions](#setup-instructions)
- [Usage examples](#usage-examples)
- [Architecture overview](#architecture-overview)
- [Assumptions & trade-offs](#assumptions--trade-offs)
- [Testing instructions](#testing-instructions)
- [Bonus features implemented](#bonus-features-implemented)
- [Demo](#demo)

## Tech stack

| Concern            | Choice                                                        |
|---------------------|----------------------------------------------------------------|
| Language / runtime  | Java 17                                                         |
| Framework           | Spring Boot 3.5 (non-web, `CommandLineRunner`)                  |
| CLI parsing         | [picocli](https://picocli.info/) (Spring-managed commands)      |
| Persistence         | MySQL 8+ via Spring Data JPA / Hibernate                        |
| Schema migrations   | Flyway                                                          |
| Build               | Maven                                                           |
| Tests               | JUnit 5, Mockito, AssertJ                                       |

## Setup instructions

### Prerequisites

- JDK 17+
- Maven 3.9+ (or use your own `mvn`; no wrapper is committed, any recent Maven works)
- MySQL 8.0+ running locally or reachable over the network (needed for `SELECT ... FOR UPDATE
  SKIP LOCKED`, which the worker claim logic relies on)

### 1. Create the database

```sql
CREATE DATABASE IF NOT EXISTS queuectl CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'queuectl'@'localhost' IDENTIFIED BY '<choose-a-password>';
GRANT ALL PRIVILEGES ON queuectl.* TO 'queuectl'@'localhost';
FLUSH PRIVILEGES;
```

Run that with `mysql -u root -p < schema-setup.sql` or paste it into a MySQL client. Tables are
created automatically by Flyway the first time `queuectl` runs — no manual DDL needed beyond
creating the database itself.

### 2. Configure connection settings

All configuration is via environment variables (nothing is hardcoded); every var has a
sane local-dev default except the password:

| Variable                | Default       | Purpose                          |
|--------------------------|---------------|-----------------------------------|
| `QUEUECTL_DB_HOST`       | `localhost`   | MySQL host                        |
| `QUEUECTL_DB_PORT`       | `3306`        | MySQL port                        |
| `QUEUECTL_DB_NAME`       | `queuectl`    | Database name                     |
| `QUEUECTL_DB_USER`       | `root`        | DB user                           |
| `QUEUECTL_DB_PASSWORD`   | *(empty)*     | DB password                       |
| `QUEUECTL_LOG_LEVEL`     | `INFO`        | Log level for `com.queuectl.*`    |

```bash
export QUEUECTL_DB_HOST=localhost
export QUEUECTL_DB_NAME=queuectl
export QUEUECTL_DB_USER=queuectl
export QUEUECTL_DB_PASSWORD=<your-password>
```

(PowerShell: `$env:QUEUECTL_DB_USER = "queuectl"`, etc.)

### 3. Build

```bash
mvn package
```

This produces `target/queuectl.jar`, a self-contained executable jar (Flyway migrations run
automatically the first time it connects).

### 4. Run

```bash
java -jar target/queuectl.jar <command>
# or, using the wrapper scripts checked into the repo root:
./queuectl.sh <command>      # Linux/macOS
./queuectl.ps1 <command>     # Windows PowerShell
```

Every invocation of `queuectl` is its own short-lived process — including `worker start`, which
is the one command that blocks and runs in the foreground until you stop it (see below).

## Usage examples

```text
$ queuectl enqueue '{"id":"job1","command":"echo Hello World"}'
Enqueued job:
{
  "id" : "job1",
  "command" : "echo Hello World",
  "state" : "pending",
  "attempts" : 0,
  "max_retries" : 3,
  "backoff_base" : 2,
  "priority" : 0,
  "created_at" : "2026-07-17T06:36:07.976999300Z",
  "updated_at" : "2026-07-17T06:36:07.976999300Z"
}

$ queuectl worker start --count 3
Worker process 4f8436b6-83f4-41fd-be4b-4e41498517a9 started (pid=6944, threads=3)
Run 'queuectl worker stop' (or Ctrl+C) to shut down gracefully.
# ... runs in the foreground, processing jobs, until stopped ...

# from another terminal:
$ queuectl worker stop
Stop requested for 1 worker process(es). Waiting for graceful shutdown...
All targeted worker processes have stopped.

$ queuectl status
Job states:
  pending:    0
  processing: 0
  completed:  1
  failed:     0
  dead:       0

Active workers: 0

$ queuectl list --state completed
ID                                   STATE       ATTEMPTS  MAX  UPDATED_AT                    COMMAND
job1                                 completed   0         3    2026-07-17T06:37:15.869027Z    echo Hello World

$ queuectl enqueue '{"id":"badjob","command":"exit 1","max_retries":2,"backoff_base":1}'
$ queuectl worker start --count 1     # ... let it retry twice, then Ctrl+C ...
$ queuectl dlq list
ID                                   STATE       ATTEMPTS  MAX  UPDATED_AT                    COMMAND
badjob                               dead        2         2    2026-07-17T06:38:22.262242Z    exit 1

$ queuectl dlq retry badjob
Job 'badjob' re-queued (state=pending, attempts reset to 0).

$ queuectl config set max-retries 5
max-retries = 5

$ queuectl config get
max-retries = 5
backoff-base = 2
poll-interval-ms = 1000
job-timeout-seconds = 300
stale-processing-seconds = 120
heartbeat-interval-ms = 5000
```

Every command supports `-h`/`--help`, e.g. `queuectl worker start --help`.

### All commands

| Command                                | Description                                             |
|------------------------------------------|-----------------------------------------------------------|
| `enqueue '<json>'`                      | Add a new job. `command` is required; everything else is optional (`id`, `max_retries`, `backoff_base`, `priority`, `run_at`). |
| `worker start --count N`                | Start N worker threads in the foreground.                |
| `worker stop [--id ID] [--wait-seconds S]` | Gracefully stop worker process(es).                    |
| `status`                                | Job state counts + active worker processes.               |
| `list [--state S] [--limit N]`          | List jobs, optionally filtered by state.                  |
| `dlq list [--limit N]`                  | List dead-lettered jobs.                                   |
| `dlq retry <job_id>`                    | Re-queue a dead job (resets attempts to 0).                |
| `config set <key> <value>`              | Set a config value (see keys in the example above).        |
| `config get [key]`                      | Show one or all config values.                              |

## Architecture overview

```
                         ┌────────────────────┐
   queuectl enqueue ───► │                    │
   queuectl list    ───► │       MySQL        │ ◄─── queuectl status / dlq / config
   queuectl config  ───► │  jobs, worker_      │
                         │  processes,          │
   queuectl worker   ┐   │  config_entries      │
   start (process A) │   └──────────┬──────────┘
                      │              │ SELECT ... FOR UPDATE SKIP LOCKED
                      ▼              │ (atomic claim, no duplicate work)
              ┌───────────────┐      │
              │ thread pool   │◄─────┘
              │ (--count N)   │
              └───────┬───────┘
                      │ ProcessBuilder
                      ▼
              child process (the job's `command`)
              stdout/stderr ──► logs/<job_id>-attempt-<n>.log
```

### Job lifecycle

```
enqueue ──► pending ──(claimed by a worker)──► processing ──► completed
                ▲                                  │
                │ backoff elapsed                  │ exit code != 0
                │                                   ▼
              failed  ◄────────────────────  (attempts < max_retries)
                │
                │ attempts >= max_retries
                ▼
               dead  (DLQ) ──(dlq retry)──► pending
```

- **pending**: eligible for a worker to claim (and `run_at`, for delayed jobs, has elapsed).
- **processing**: claimed by a worker; the command is currently running.
- **completed**: the command exited 0.
- **failed**: the command exited non-zero (or couldn't be started, or timed out), attempts
  incremented, and `next_run_at = now + backoff_base^attempts` seconds — still retryable.
- **dead**: `attempts >= max_retries`; parked in the DLQ until `dlq retry` resets it.

### Data persistence

MySQL was chosen over a flat JSON file specifically **because** of `SELECT ... FOR UPDATE SKIP
LOCKED`: it gives correct, non-blocking, at-most-once job claiming across concurrent workers for
free, instead of hand-rolling file locking. Three tables (see
`src/main/resources/db/migration/V1__init.sql`):

- `jobs` — one row per job; the full job lifecycle above lives here.
- `worker_processes` — one row per `worker start` invocation (pid, thread count, heartbeat,
  status), used by `status` and by the stop-signalling mechanism below.
- `config_entries` — key/value runtime configuration (`max-retries`, `backoff-base`, etc.), seeded
  with defaults by the migration and mutated by `config set`.

Because everything lives in MySQL rather than in-process memory or a per-process file, job data
(and its exact state — attempts, last error, timestamps) survives process restarts and machine
reboots by construction: every `queuectl` invocation is a fresh process that reads current state
from the same database.

### Worker logic

Each `worker start --count N` invocation is one OS process running N worker **threads** against a
shared `ExecutorService`. Every thread independently loops: claim a job, run it, repeat.

Claiming uses this query (`JobRepository.findNextClaimableId`):

```sql
SELECT id FROM jobs
WHERE (state = 'PENDING' AND (run_at IS NULL OR run_at <= NOW()))
   OR (state = 'FAILED' AND next_run_at <= NOW())
   OR (state = 'PROCESSING' AND locked_at <= :staleThreshold)
ORDER BY priority DESC, created_at ASC
LIMIT 1
FOR UPDATE SKIP LOCKED
```

`FOR UPDATE SKIP LOCKED` row-locks the candidate and lets any other concurrent claimer skip past
it instead of blocking — this is what prevents duplicate processing. It's implemented at the
database level, not with an in-memory mutex, so it stays correct even if you run **multiple
separate `worker start` processes** against the same database, not just multiple threads in one
process. The third clause reclaims jobs abandoned by a worker that crashed mid-job (`locked_at`
older than `stale-processing-seconds`), so a hard kill -9 doesn't strand a job in `processing`
forever.

**Graceful shutdown** is the one tricky part, because `worker start` and `worker stop` are
*separate CLI invocations* (separate JVMs) — `stop` can't just call a method on the running
process. Instead:

1. `worker stop` flips the running process's row in `worker_processes` to `STOPPING`.
2. The running process has a lightweight heartbeat thread polling its own row every
   `heartbeat-interval-ms`; on seeing `STOPPING` it flips a local `AtomicBoolean`.
3. Each worker thread checks that flag **between** jobs, never mid-job, and exits its loop once
   set — so an in-flight command always finishes before the thread stops.
4. Once every thread has exited, the process marks itself `STOPPED` and the JVM exits normally.
5. Ctrl+C / SIGTERM is also handled: a JVM shutdown hook sets the same flag and blocks (joins the
   worker threads, up to `job-timeout-seconds + 10s`) before letting the JVM actually halt, so a
   kill signal drains in-flight work the same way a `worker stop` does.

`status` surfaces all `RUNNING`/`STOPPING` rows from `worker_processes` (pid, thread count,
status, last heartbeat) so you can see what's active without needing OS-level process inspection.

### Retry & backoff

On failure, `JobService.markFailure` increments `attempts` and computes
`delay = backoff_base ^ attempts` seconds (`BackoffCalculator`), setting `next_run_at`
accordingly; once `attempts >= max_retries` the job goes straight to `dead` instead. Both
`max_retries` and `backoff_base` are resolved per-job at enqueue time (from the request JSON, or
the global config default if omitted) and stored on the job row itself — so a later
`config set backoff-base` only affects jobs enqueued afterward, not jobs already in flight (see
trade-offs below).

### Job execution

`JobExecutionService` runs each job's `command` via `ProcessBuilder` (`cmd.exe /c` on Windows,
`sh -c` elsewhere), with stdout+stderr redirected to `logs/<job_id>-attempt-<n>.log`. Exit code 0
is success; anything else (including "command not found", which surfaces as a non-zero exit or an
`IOException` from `ProcessBuilder.start()`) is a failure and goes through the retry/backoff path
above — so unresolvable commands degrade to a normal retry-then-DLQ, not a crash. A configurable
`job-timeout-seconds` bounds how long a single attempt can run before it's force-killed and
counted as a failed attempt.

## Assumptions & trade-offs

- **Threads-per-process, not one-OS-process-per-worker.** `worker start --count 3` runs 3 threads
  in one JVM rather than forking 3 OS processes. This is simpler to operate (one thing to start,
  stop, and monitor) and the DB-level `SKIP LOCKED` claim means correctness doesn't depend on this
  choice — running several separate `worker start` processes against the same database also works
  correctly, if you want real OS-level parallelism/isolation instead.
- **Config changes are not retroactive.** `max_retries`/`backoff_base` are captured on the job at
  enqueue time rather than re-read from `config_entries` on every retry. This matches the job spec
  (which already includes `max_retries` as a per-job field) and avoids surprising behavior where
  changing global config mid-flight silently alters an already-running job's retry budget.
  `config set` only affects jobs enqueued after the change.
- **Stale-processing reclaim, not a dedicated crash detector.** If a worker process is killed
  (not gracefully stopped) mid-job, its job is left in `processing` until another worker's claim
  query notices `locked_at` is older than `stale-processing-seconds` (default 120s) and reclaims
  it. This is a deliberately simple heuristic rather than a full liveness/heartbeat-per-job system.
- **Job output goes to a local `logs/` directory**, keyed by job id + attempt number, rather than
  into the database — full stdout/stderr can be arbitrarily large, so only a short pointer/summary
  (`last_error`, referencing the log file) is stored on the job row.
- **MySQL is a hard dependency**, not an option alongside a flat-file/SQLite mode. The assignment
  allows "JSON, SQLite, or anything you think is best"; MySQL's `SKIP LOCKED` was specifically
  what made concurrent-safe claiming straightforward, so the project leans into it rather than
  supporting multiple storage backends.
- **`queuectl worker stop` waits synchronously** (default up to 30s, `--wait-seconds`) for
  confirmation that the target process(es) actually stopped, rather than firing-and-forgetting the
  stop signal — this gives scripts/tests a reliable way to know shutdown actually completed.

## Testing instructions

### Unit tests

```bash
mvn test
```

Covers `BackoffCalculator` (exponential delay math), `JobService` state transitions (enqueue
validation/defaults, failure → retry vs. failure → DLQ, DLQ retry, success), and enqueue JSON
parsing (including tolerating a full job JSON with server-managed fields like `state`/`attempts`
pasted back in).

### End-to-end smoke test

```bash
mvn package
export QUEUECTL_DB_NAME=queuectl QUEUECTL_DB_USER=queuectl QUEUECTL_DB_PASSWORD=<your-password>
bash scripts/smoke-test.sh
```

Drives the real CLI against a real MySQL database end-to-end and asserts on the five scenarios
called out in the assignment: a job completing successfully, a failing job retrying with backoff
and reaching the DLQ, multiple worker threads draining a batch of jobs without duplication, an
unresolvable command failing gracefully instead of crashing, and job data surviving process
restarts (every step is already a fresh JVM process reading shared MySQL state). Note: because
each CLI invocation is a fresh Spring Boot process, the full script takes a few minutes to run —
that startup cost is a smoke-test artifact of re-launching the JVM for every check, not a
reflection of per-job latency (see the log output, where jobs complete in milliseconds).

## Bonus features implemented

- ✅ **Job timeout handling** — `job-timeout-seconds` config, enforced per attempt via
  `Process.waitFor(timeout, ...)` + `destroyForcibly()`.
- ✅ **Job priority** — optional `priority` field on enqueue; claim query orders by
  `priority DESC, created_at ASC`.
- ✅ **Scheduled/delayed jobs** — optional `run_at` (ISO-8601) field; a job isn't claimable until
  that time has elapsed.
- ✅ **Job output logging** — every attempt's stdout/stderr is captured to
  `logs/<job_id>-attempt-<n>.log`.

Not implemented (out of scope for this pass): metrics/execution-stats export and a web dashboard.

## Demo

<!-- Record a short CLI walkthrough (enqueue → worker start → retry/backoff → DLQ → dlq retry →
     restart persistence), upload it, and put the link here before submitting. -->

_Demo video: TODO — add link here._
