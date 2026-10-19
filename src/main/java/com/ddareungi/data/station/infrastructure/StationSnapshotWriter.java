package com.ddareungi.data.station.infrastructure;

import com.ddareungi.data.station.domain.StationSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Repository
public class StationSnapshotWriter {
    private static final String UPSERT_STATION = """
            INSERT INTO station
              (station_id, station_name, latitude, longitude, rack_total_count, realtime_updated_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE station_name=VALUES(station_name), latitude=VALUES(latitude),
              longitude=VALUES(longitude), rack_total_count=VALUES(rack_total_count),
              realtime_updated_at=VALUES(realtime_updated_at), updated_at=VALUES(updated_at)
            """;
    private static final String UPSERT_HISTORY = """
            INSERT INTO station_history
              (station_id, station_name, station_dt, rack_total_count, parking_bike_total_count,
               shared, latitude, longitude, collected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE station_name=VALUES(station_name), rack_total_count=VALUES(rack_total_count),
              parking_bike_total_count=VALUES(parking_bike_total_count), shared=VALUES(shared),
              latitude=VALUES(latitude), longitude=VALUES(longitude), collected_at=VALUES(collected_at)
            """;

    private final JdbcTemplate jdbcTemplate;
    private static final String UPSERT_INVENTORY = """
            INSERT INTO station_inventory_5m (station_id, observed_slot, available_bikes, rack_count, latitude, longitude, collected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE available_bikes=VALUES(available_bikes), rack_count=VALUES(rack_count),
              latitude=VALUES(latitude), longitude=VALUES(longitude), collected_at=VALUES(collected_at)
            """;

    public StationSnapshotWriter(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public void upsert(List<StationSnapshot> snapshots) {
        snapshots=snapshots.stream().sorted(java.util.Comparator.comparing(StationSnapshot::stationId)).toList();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        jdbcTemplate.batchUpdate(UPSERT_STATION, snapshots, 500, (statement, item) -> {
            statement.setString(1, item.stationId()); statement.setString(2, item.stationName());
            statement.setBigDecimal(3, item.latitude()); statement.setBigDecimal(4, item.longitude());
            statement.setInt(5, item.rackTotalCount()); statement.setTimestamp(6, Timestamp.valueOf(now));
            statement.setTimestamp(7, Timestamp.valueOf(now)); statement.setTimestamp(8, Timestamp.valueOf(now));
        });
        jdbcTemplate.batchUpdate(UPSERT_HISTORY, snapshots, 500, (statement, item) -> {
            statement.setString(1, item.stationId()); statement.setString(2, item.stationName());
            statement.setTimestamp(3, Timestamp.valueOf(item.stationDateTime().withMinute(0))); statement.setInt(4, item.rackTotalCount());
            statement.setInt(5, item.parkingBikeTotalCount()); statement.setBigDecimal(6, item.shared());
            statement.setBigDecimal(7, item.latitude()); statement.setBigDecimal(8, item.longitude());
            statement.setTimestamp(9, Timestamp.valueOf(now));
        });
        jdbcTemplate.batchUpdate(UPSERT_INVENTORY, snapshots, 500, (statement, item) -> {
            statement.setString(1, item.stationId()); statement.setTimestamp(2, Timestamp.valueOf(item.stationDateTime()));
            statement.setInt(3, item.parkingBikeTotalCount()); statement.setInt(4, item.rackTotalCount());
            statement.setBigDecimal(5, item.latitude()); statement.setBigDecimal(6, item.longitude());
            statement.setTimestamp(7, Timestamp.valueOf(now));
        });
    }
}
