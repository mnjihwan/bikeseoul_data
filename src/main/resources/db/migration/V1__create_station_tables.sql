CREATE TABLE station (
    station_id VARCHAR(30) PRIMARY KEY,
    station_name VARCHAR(255) NULL,
    address1 VARCHAR(500) NULL,
    address2 VARCHAR(500) NULL,
    latitude DECIMAL(10, 7) NULL,
    longitude DECIMAL(10, 7) NULL,
    rack_total_count INT NULL,
    master_updated_at DATETIME NULL,
    realtime_updated_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_station_location (latitude, longitude)
);

CREATE TABLE station_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    station_id VARCHAR(30) NOT NULL,
    station_name VARCHAR(255) NOT NULL,
    station_dt DATETIME NOT NULL,
    rack_total_count INT NOT NULL,
    parking_bike_total_count INT NOT NULL,
    shared DECIMAL(8, 2) NOT NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    collected_at DATETIME NOT NULL,
    CONSTRAINT uk_station_history_station_dt UNIQUE (station_id, station_dt),
    INDEX idx_station_history_dt (station_dt),
    INDEX idx_station_history_station (station_id)
);

CREATE TABLE station_flow_5m (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_date DATE NOT NULL,
    aggregation_basis VARCHAR(30) NOT NULL,
    time_slot TIME NOT NULL,
    start_station_id VARCHAR(30) NOT NULL,
    start_station_name VARCHAR(255) NOT NULL,
    end_station_id VARCHAR(30) NOT NULL,
    end_station_name VARCHAR(255) NOT NULL,
    ride_count INT NOT NULL,
    usage_minutes BIGINT NOT NULL,
    distance_meters BIGINT NOT NULL,
    source_file VARCHAR(255) NOT NULL,
    imported_at DATETIME NOT NULL,
    CONSTRAINT uk_station_flow UNIQUE
      (service_date, aggregation_basis, time_slot, start_station_id, end_station_id),
    INDEX idx_station_flow_start (start_station_id, service_date, time_slot),
    INDEX idx_station_flow_end (end_station_id, service_date, time_slot)
);

CREATE TABLE data_import_manifest (
    source_file VARCHAR(255) NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    row_count BIGINT NOT NULL,
    imported_at DATETIME NOT NULL,
    PRIMARY KEY (source_file, checksum_sha256)
);
