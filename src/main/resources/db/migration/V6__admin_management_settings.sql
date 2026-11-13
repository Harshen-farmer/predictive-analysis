CREATE TABLE allocation_settings (
    id BIGINT PRIMARY KEY,
    warning_percent DOUBLE PRECISION NOT NULL,
    shortage_percent DOUBLE PRECISION NOT NULL,
    CONSTRAINT ck_allocation_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_allocation_settings_thresholds
        CHECK (warning_percent >= 0 AND shortage_percent > warning_percent)
);

INSERT INTO allocation_settings (id, warning_percent, shortage_percent) VALUES (1, 80, 100);
