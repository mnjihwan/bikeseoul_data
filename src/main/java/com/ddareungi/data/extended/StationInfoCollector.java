package com.ddareungi.data.extended;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class StationInfoCollector {
    private final SeoulDatasetClient client; private final JdbcTemplate jdbc;
    public StationInfoCollector(SeoulDatasetClient client,JdbcTemplate jdbc){this.client=client;this.jdbc=jdbc;}
    public static String number(String value) {
        if (value==null || !value.matches("[0-9]+")) throw new IllegalArgumentException("INVALID_STATION_NUMBER");
        return new java.math.BigInteger(value).toString();
    }
    public static Integer optionalCount(String value){if(value==null||value.isBlank())return null;int count=new BigDecimal(value.replace(",","")).intValueExact();if(count<0)throw new IllegalArgumentException("NEGATIVE_RACKS");return count;}
    @Transactional
    public int collect() {
        var rows=client.fetch("tbCycleStationInfo","stationInfo","").stream()
            .sorted(java.util.Comparator.comparing(row->row.path("RENT_ID").asString(""))).toList();
        if (rows.isEmpty()) return 0;
        var now=Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        jdbc.batchUpdate("""
            INSERT INTO station (station_id,station_number,station_name,district,address1,address2,latitude,longitude,rack_total_count,master_updated_at,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE station_number=VALUES(station_number),station_name=VALUES(station_name),district=VALUES(district),
              address1=VALUES(address1),address2=VALUES(address2),latitude=COALESCE(VALUES(latitude),latitude),
              longitude=COALESCE(VALUES(longitude),longitude),rack_total_count=COALESCE(rack_total_count,VALUES(rack_total_count)),
              master_updated_at=VALUES(master_updated_at),updated_at=VALUES(updated_at)
            """, rows,500,(ps,row)->{
                String id=row.path("RENT_ID").asString(""),name=row.path("RENT_ID_NM").asString("");
                if (!id.matches("ST-[0-9]+") || name.isBlank()) throw new IllegalArgumentException("INVALID_STATION_INFO");
                ps.setString(1,id);ps.setString(2,number(row.path("RENT_NO").asString("")));ps.setString(3,name);
                ps.setString(4,row.path("STA_LOC").asString(""));ps.setString(5,row.path("STA_ADD1").asString(""));ps.setString(6,row.path("STA_ADD2").asString(""));
                var lat=new BigDecimal(row.path("STA_LAT").asString("0"));var lon=new BigDecimal(row.path("STA_LONG").asString("0"));
                if (lat.signum()==0 && lon.signum()==0){ps.setNull(7,java.sql.Types.DECIMAL);ps.setNull(8,java.sql.Types.DECIMAL);}
                else {if(lat.abs().compareTo(BigDecimal.valueOf(90))>0||lon.abs().compareTo(BigDecimal.valueOf(180))>0)throw new IllegalArgumentException("INVALID_COORDINATES");ps.setBigDecimal(7,lat);ps.setBigDecimal(8,lon);}
                var racks=optionalCount(row.path("HOLD_NUM").asString(""));if(racks==null)ps.setNull(9,java.sql.Types.INTEGER);else ps.setInt(9,racks);
                ps.setTimestamp(10,now);ps.setTimestamp(11,now);ps.setTimestamp(12,now);
            });
        return rows.size();
    }
}
