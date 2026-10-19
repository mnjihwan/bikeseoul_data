package com.ddareungi.data.station.infrastructure;

import com.ddareungi.data.station.domain.StationMaster;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class StationMasterWriter {
    private static final String UPSERT = """
            INSERT INTO station
              (station_id, address1, address2, latitude, longitude, master_updated_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE address1=VALUES(address1), address2=VALUES(address2),
              latitude=COALESCE(VALUES(latitude), latitude), longitude=COALESCE(VALUES(longitude), longitude),
              master_updated_at=VALUES(master_updated_at), updated_at=VALUES(updated_at)
            """;

    private final JdbcTemplate jdbcTemplate;

    public StationMasterWriter(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public void upsert(List<StationMaster> stations) {
        stations=stations.stream().sorted(java.util.Comparator.comparing(StationMaster::stationId)).toList();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.batchUpdate(UPSERT, stations, 500, (statement, station) -> {
            statement.setString(1, station.stationId());
            statement.setString(2, station.address1());
            statement.setString(3, station.address2());
            if (station.latitude() == null) statement.setNull(4, Types.DECIMAL);
            else statement.setBigDecimal(4, station.latitude());
            if (station.longitude() == null) statement.setNull(5, Types.DECIMAL);
            else statement.setBigDecimal(5, station.longitude());
            statement.setTimestamp(6, Timestamp.valueOf(now));
            statement.setTimestamp(7, Timestamp.valueOf(now));
            statement.setTimestamp(8, Timestamp.valueOf(now));
        });
    }
}
