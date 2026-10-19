ALTER TABLE station ADD COLUMN station_number VARCHAR(30) NULL, ADD COLUMN district VARCHAR(100) NULL,
    ADD INDEX idx_station_number (station_number);

CREATE TABLE station_inventory_5m (
    station_id VARCHAR(30) NOT NULL, observed_slot DATETIME NOT NULL,
    available_bikes INT NOT NULL, rack_count INT NOT NULL, latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL, collected_at DATETIME NOT NULL,
    PRIMARY KEY (station_id, observed_slot), INDEX idx_inventory_slot (observed_slot)
);

CREATE TABLE station_usage (
    dataset_id VARCHAR(30) NOT NULL, resolution VARCHAR(10) NOT NULL, period_date DATE NOT NULL,
    hour_of_day SMALLINT NOT NULL, station_number VARCHAR(30) NOT NULL, station_name VARCHAR(255) NOT NULL,
    rent_count BIGINT NOT NULL, usage_minutes DECIMAL(24,4) NULL, distance_meters DECIMAL(24,4) NULL,
    source_file VARCHAR(255) NOT NULL, collected_at DATETIME NOT NULL,
    PRIMARY KEY (dataset_id, resolution, period_date, hour_of_day, station_number),
    INDEX idx_usage_period (period_date), INDEX idx_usage_source (source_file)
);

CREATE TABLE collection_run (
    source_key VARCHAR(255) PRIMARY KEY, status VARCHAR(20) NOT NULL,
    source_rows BIGINT NOT NULL DEFAULT 0, stored_rows BIGINT NOT NULL DEFAULT 0,
    last_attempt DATETIME NOT NULL, error_code VARCHAR(100) NULL, content_checksum CHAR(64) NULL
);

CREATE VIEW station_usage_with_id AS
SELECT u.*, s.station_id FROM station_usage u LEFT JOIN
    (SELECT station_number, MIN(station_id) AS station_id FROM station
     WHERE station_number IS NOT NULL GROUP BY station_number HAVING COUNT(*)=1) s
    ON s.station_number=u.station_number;
