CREATE TABLE jobs (
    id                VARCHAR(64)   NOT NULL,
    command           TEXT          NOT NULL,
    state             VARCHAR(16)   NOT NULL,
    attempts          INT           NOT NULL DEFAULT 0,
    max_retries       INT           NOT NULL DEFAULT 3,
    backoff_base      INT           NOT NULL DEFAULT 2,
    priority          INT           NOT NULL DEFAULT 0,
    run_at            DATETIME(6)   NULL,
    next_run_at       DATETIME(6)   NULL,
    locked_by         VARCHAR(64)   NULL,
    locked_at         DATETIME(6)   NULL,
    last_error        TEXT          NULL,
    started_at        DATETIME(6)   NULL,
    completed_at      DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_jobs_state (state),
    INDEX idx_jobs_state_next_run_at (state, next_run_at),
    INDEX idx_jobs_run_at (run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE worker_processes (
    id                VARCHAR(64)   NOT NULL,
    pid               BIGINT        NOT NULL,
    thread_count      INT           NOT NULL,
    status            VARCHAR(16)   NOT NULL,
    started_at        DATETIME(6)   NOT NULL,
    last_heartbeat    DATETIME(6)   NOT NULL,
    stopped_at        DATETIME(6)   NULL,
    PRIMARY KEY (id),
    INDEX idx_worker_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE config_entries (
    config_key        VARCHAR(64)   NOT NULL,
    config_value      VARCHAR(255)  NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO config_entries (config_key, config_value, updated_at) VALUES
    ('max-retries',              '3',    UTC_TIMESTAMP(6)),
    ('backoff-base',             '2',    UTC_TIMESTAMP(6)),
    ('poll-interval-ms',         '1000', UTC_TIMESTAMP(6)),
    ('job-timeout-seconds',      '300',  UTC_TIMESTAMP(6)),
    ('stale-processing-seconds', '120',  UTC_TIMESTAMP(6)),
    ('heartbeat-interval-ms',    '5000', UTC_TIMESTAMP(6));
