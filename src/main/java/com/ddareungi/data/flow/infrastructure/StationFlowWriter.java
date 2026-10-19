package com.ddareungi.data.flow.infrastructure;

import com.ddareungi.data.flow.domain.StationFlow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class StationFlowWriter {
    private static final String UPSERT = """
            INSERT INTO station_flow_5m
              (service_date, aggregation_basis, time_slot, start_station_id, start_station_name,
               end_station_id, end_station_name, ride_count, usage_minutes, distance_meters,
               source_file, imported_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE start_station_name=VALUES(start_station_name),
              end_station_name=VALUES(end_station_name), ride_count=VALUES(ride_count),
              usage_minutes=VALUES(usage_minutes), distance_meters=VALUES(distance_meters),
              source_file=VALUES(source_file), imported_at=VALUES(imported_at)
            """;

    private final JdbcTemplate jdbcTemplate;

    public StationFlowWriter(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    @FunctionalInterface public interface FileImport { long run() throws Exception; }
    @Transactional(rollbackFor=Exception.class)
    public long replaceFile(String sourceFile,String checksum,FileImport operation) throws Exception {
        jdbcTemplate.update("DELETE FROM station_flow_5m WHERE source_file=?",sourceFile);
        long rows=operation.run();
        if(rows==0)throw new IllegalArgumentException("EMPTY_OD_FILE");
        jdbcTemplate.update("DELETE FROM data_import_manifest WHERE source_file=?",sourceFile);
        recordImport(sourceFile,checksum,rows);
        recordCollection(sourceFile,"COMPLETED",rows,checksum);
        return rows;
    }

    public void recordEmpty(String sourceFile,String checksum){recordCollection(sourceFile,"EMPTY",0,checksum);}
    private void recordCollection(String sourceFile,String status,long rows,String checksum){
        jdbcTemplate.update("""
            INSERT INTO collection_run(source_key,status,source_rows,stored_rows,last_attempt,error_code,content_checksum)
            VALUES(?,?,?,?,?,NULL,?) ON DUPLICATE KEY UPDATE status=VALUES(status),source_rows=VALUES(source_rows),
            stored_rows=VALUES(stored_rows),last_attempt=VALUES(last_attempt),error_code=NULL,content_checksum=VALUES(content_checksum)
            ""","od:"+sourceFile,status,rows,rows,Timestamp.valueOf(LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"))),checksum);
    }

    public void upsert(List<StationFlow> rows, String sourceFile) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.batchUpdate(UPSERT, rows, rows.size(), (statement, row) -> {
            statement.setDate(1, Date.valueOf(row.serviceDate()));
            statement.setString(2, row.aggregationBasis());
            statement.setTime(3, Time.valueOf(row.timeSlot()));
            statement.setString(4, row.startStationId());
            statement.setString(5, row.startStationName());
            statement.setString(6, row.endStationId());
            statement.setString(7, row.endStationName());
            statement.setInt(8, row.rideCount());
            if(row.usageMinutes()==null)statement.setNull(9,java.sql.Types.BIGINT);else statement.setLong(9,row.usageMinutes());
            if(row.distanceMeters()==null)statement.setNull(10,java.sql.Types.BIGINT);else statement.setLong(10,row.distanceMeters());
            statement.setString(11, sourceFile);
            statement.setTimestamp(12, Timestamp.valueOf(now));
        });
    }

    public boolean wasImported(String sourceFile, String checksum) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_import_manifest WHERE source_file = ? AND checksum_sha256 = ?",
                Integer.class, sourceFile, checksum);
        return count != null && count > 0;
    }

    public void recordImport(String sourceFile, String checksum, long rowCount) {
        jdbcTemplate.update("""
                        INSERT INTO data_import_manifest (source_file, checksum_sha256, row_count, imported_at)
                        VALUES (?, ?, ?, ?)
                        """, sourceFile, checksum, rowCount, Timestamp.valueOf(LocalDateTime.now()));
    }
}
