package com.ddareungi.data.extended;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Repository
public class UsageWriter {
    private final JdbcTemplate jdbc;
    public UsageWriter(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional
    public void replace(String source,List<UsageRow> rows,long sourceRows,String checksum) {
        if(rows.isEmpty())throw new IllegalArgumentException("EMPTY_USAGE_REPLACEMENT");
        jdbc.update("DELETE FROM station_usage WHERE source_file=?",source);
        var now=Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        jdbc.batchUpdate("""
            INSERT INTO station_usage (dataset_id,resolution,period_date,hour_of_day,station_number,station_name,rent_count,usage_minutes,distance_meters,source_file,collected_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE station_name=VALUES(station_name),rent_count=VALUES(rent_count),
              usage_minutes=VALUES(usage_minutes),distance_meters=VALUES(distance_meters),source_file=VALUES(source_file),collected_at=VALUES(collected_at)
            """,rows,1000,(ps,row)->{
                ps.setString(1,row.dataset());ps.setString(2,row.resolution());ps.setDate(3,java.sql.Date.valueOf(row.date()));ps.setInt(4,row.hour());
                ps.setString(5,row.number());ps.setString(6,row.name());ps.setLong(7,row.count());ps.setBigDecimal(8,row.minutes());ps.setBigDecimal(9,row.meters());
                ps.setString(10,source);ps.setTimestamp(11,now);
            });
        record(source,"COMPLETED",sourceRows,rows.size(),null);
        jdbc.update("UPDATE collection_run SET content_checksum=? WHERE source_key=?",checksum,source);
    }
    public boolean imported(String source,String checksum){return jdbc.queryForObject("SELECT COUNT(*) FROM collection_run WHERE source_key=? AND status='COMPLETED' AND content_checksum=?",Integer.class,source,checksum)>0;}
    public void record(String source,String status,long sourceRows,long stored,String error) {
        jdbc.update("""
            INSERT INTO collection_run (source_key,status,source_rows,stored_rows,last_attempt,error_code) VALUES (?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE status=VALUES(status),source_rows=VALUES(source_rows),stored_rows=VALUES(stored_rows),last_attempt=VALUES(last_attempt),error_code=VALUES(error_code),content_checksum=NULL
            """,source,status,sourceRows,stored,Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul"))),error);
    }
}
