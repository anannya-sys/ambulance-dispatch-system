-- GoldenHour schema. PostGIS gives us geography columns and GiST indexes for nearest-neighbour search.
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE hospital (
    id               BIGINT PRIMARY KEY,
    name             VARCHAR(120) NOT NULL,
    lat              DOUBLE PRECISION NOT NULL,
    lon              DOUBLE PRECISION NOT NULL,
    -- computed by Postgres from lat/lon, so JPA only ever writes plain numbers
    location         GEOGRAPHY(POINT, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography) STORED,
    road_node        INT NOT NULL,
    table_index      INT NOT NULL,
    capabilities     VARCHAR(120) NOT NULL,
    icu_total        INT NOT NULL, icu_free     INT NOT NULL,
    cardiac_total    INT NOT NULL, cardiac_free INT NOT NULL,
    trauma_total     INT NOT NULL, trauma_free  INT NOT NULL,
    general_total    INT NOT NULL, general_free INT NOT NULL,
    er_queue         INT NOT NULL,
    er_bays          INT NOT NULL,
    specialists_24x7 BOOLEAN NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0   -- optimistic locking for bed reservations
);
CREATE INDEX hospital_location_gix ON hospital USING GIST (location);

CREATE TABLE standby_post (
    id        INT PRIMARY KEY,
    name      VARCHAR(80) NOT NULL,
    lat       DOUBLE PRECISION NOT NULL,
    lon       DOUBLE PRECISION NOT NULL,
    road_node INT NOT NULL
);

CREATE TABLE ambulance (
    id              BIGSERIAL PRIMARY KEY,
    call_sign       VARCHAR(20) NOT NULL UNIQUE,
    kind            VARCHAR(10) NOT NULL,             -- ALS (advanced life support) or BLS (basic)
    status          VARCHAR(20) NOT NULL,
    lat             DOUBLE PRECISION NOT NULL,
    lon             DOUBLE PRECISION NOT NULL,
    location        GEOGRAPHY(POINT, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography) STORED,
    road_node       INT NOT NULL,
    heading         DOUBLE PRECISION NOT NULL DEFAULT 0,
    home_post_id    INT REFERENCES standby_post (id),
    target_post_id  INT REFERENCES standby_post (id),
    current_trip_id BIGINT,
    last_ping_at    TIMESTAMP,
    available_at    TIMESTAMP,                        -- when an out-of-service unit comes back
    version         BIGINT NOT NULL DEFAULT 0
);
-- Partial GiST index: only units that can take a call are indexed, so the KNN search for
-- "nearest free ambulance" never even looks at busy ones.
CREATE INDEX ambulance_free_location_gix ON ambulance USING GIST (location)
    WHERE status IN ('AVAILABLE', 'REPOSITIONING');

CREATE TABLE incident (
    id            BIGSERIAL PRIMARY KEY,
    created_at    TIMESTAMP NOT NULL,
    lat           DOUBLE PRECISION NOT NULL,
    lon           DOUBLE PRECISION NOT NULL,
    road_node     INT NOT NULL,
    severity      VARCHAR(10) NOT NULL,
    patient_type  VARCHAR(15) NOT NULL,
    status        VARCHAR(15) NOT NULL,
    source        VARCHAR(10) NOT NULL,               -- MANUAL, AUTO (simulated caller), SURGE
    area_name     VARCHAR(60),
    description   VARCHAR(255),
    surge_group   VARCHAR(40),
    resolved_at   TIMESTAMP,
    version       BIGINT NOT NULL DEFAULT 0
);
-- The dispatch queue is "all QUEUED incidents"; a partial index keeps that scan tiny.
CREATE INDEX incident_queue_idx ON incident (created_at) WHERE status = 'QUEUED';
CREATE INDEX incident_created_idx ON incident (created_at);

CREATE TABLE trip (
    id                    BIGSERIAL PRIMARY KEY,
    incident_id           BIGINT NOT NULL REFERENCES incident (id),
    ambulance_id          BIGINT NOT NULL REFERENCES ambulance (id),
    hospital_id           BIGINT REFERENCES hospital (id),
    status                VARCHAR(15) NOT NULL,
    assigned_at           TIMESTAMP NOT NULL,
    acknowledged_at       TIMESTAMP,
    at_scene_at           TIMESTAMP,
    picked_up_at          TIMESTAMP,
    at_hospital_at        TIMESTAMP,
    closed_at             TIMESTAMP,
    eta_scene_at          TIMESTAMP,
    eta_hospital_at       TIMESTAMP,
    response_seconds      INT,
    call_to_hospital_seconds INT,
    green_corridor        BOOLEAN NOT NULL DEFAULT FALSE,
    signals_cleared       INT NOT NULL DEFAULT 0,
    hospital_reason       VARCHAR(255),
    cancel_reason         VARCHAR(120),
    route_via             VARCHAR(160),
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX trip_incident_idx ON trip (incident_id);
CREATE INDEX trip_ambulance_idx ON trip (ambulance_id);
CREATE INDEX trip_open_idx ON trip (status) WHERE status NOT IN ('CLOSED', 'CANCELLED');

CREATE TABLE trip_event (
    id      BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trip (id),
    at      TIMESTAMP NOT NULL,
    type    VARCHAR(30) NOT NULL,
    detail  VARCHAR(255)
);
CREATE INDEX trip_event_trip_idx ON trip_event (trip_id);

CREATE TABLE hospital_alert (
    id           BIGSERIAL PRIMARY KEY,
    hospital_id  BIGINT NOT NULL REFERENCES hospital (id),
    trip_id      BIGINT NOT NULL REFERENCES trip (id),
    created_at   TIMESTAMP NOT NULL,
    eta_at       TIMESTAMP NOT NULL,
    severity     VARCHAR(10) NOT NULL,
    patient_type VARCHAR(15) NOT NULL,
    bed_type     VARCHAR(10) NOT NULL,
    message      VARCHAR(255) NOT NULL
);
CREATE INDEX hospital_alert_hospital_idx ON hospital_alert (hospital_id, created_at DESC);
