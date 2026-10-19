package com.ddareungi.data.extended;
import com.ddareungi.data.flow.infrastructure.StationFlowWriter;
import com.ddareungi.data.flow.domain.StationFlow;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import java.util.List;
import java.time.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(TransactionalImportTest.Config.class)
class TransactionalImportTest {
    @Configuration @EnableTransactionManagement static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:collection;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");}
        @Bean JdbcTemplate jdbc(DataSource source){return new JdbcTemplate(source);}
        @Bean PlatformTransactionManager transactions(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean StationFlowWriter flow(JdbcTemplate jdbc){return new StationFlowWriter(jdbc);}
        @Bean UsageWriter usage(JdbcTemplate jdbc){return new UsageWriter(jdbc);}
    }
    @Autowired JdbcTemplate jdbc;@Autowired StationFlowWriter flow;@Autowired UsageWriter usage;
    @BeforeEach void schema(){
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE station_flow_5m(service_date DATE,aggregation_basis VARCHAR(30),time_slot TIME,start_station_id VARCHAR(30),start_station_name VARCHAR(255),end_station_id VARCHAR(30),end_station_name VARCHAR(255),ride_count INT,usage_minutes BIGINT,distance_meters BIGINT,source_file VARCHAR(255),imported_at TIMESTAMP,PRIMARY KEY(service_date,aggregation_basis,time_slot,start_station_id,end_station_id))");
        jdbc.execute("CREATE TABLE data_import_manifest(source_file VARCHAR(255),checksum_sha256 CHAR(64),row_count BIGINT,imported_at TIMESTAMP,PRIMARY KEY(source_file,checksum_sha256))");
        jdbc.execute("CREATE TABLE station_usage(dataset_id VARCHAR(30),resolution VARCHAR(10),period_date DATE,hour_of_day SMALLINT,station_number VARCHAR(30),station_name VARCHAR(255),rent_count BIGINT,usage_minutes DECIMAL(24,4),distance_meters DECIMAL(24,4),source_file VARCHAR(255),collected_at TIMESTAMP,PRIMARY KEY(dataset_id,resolution,period_date,hour_of_day,station_number))");
        jdbc.execute("CREATE TABLE collection_run(source_key VARCHAR(255) PRIMARY KEY,status VARCHAR(20),source_rows BIGINT,stored_rows BIGINT,last_attempt TIMESTAMP,error_code VARCHAR(100),content_checksum CHAR(64))");
    }
    StationFlow row(String id,int count){return new StationFlow(LocalDate.of(2026,9,3),"출발시간",LocalTime.MIDNIGHT,id,"출발", "ST-714","도착",count,5L,100L);}
    @Test void correctedOdFileRemovesDeletedRowsAndDoesNotMultiplyCounts()throws Exception {
        flow.replaceFile("day.csv","a",()->{flow.upsert(List.of(row("ST-10",1),row("ST-20",2)),"day.csv");return 2;});
        flow.replaceFile("day.csv","b",()->{flow.upsert(List.of(row("ST-10",3)),"day.csv");return 1;});
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM station_flow_5m",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ride_count FROM station_flow_5m",Integer.class)).isEqualTo(3);
        assertThat(flow.wasImported("day.csv","a")).isFalse();assertThat(flow.wasImported("day.csv","b")).isTrue();
    }
    @Test void lateCsvFailureRollsBackDeletionAndEarlierBatches()throws Exception {
        flow.replaceFile("day.csv","a",()->{flow.upsert(List.of(row("ST-10",1)),"day.csv");return 1;});
        assertThatThrownBy(()->flow.replaceFile("day.csv","b",()->{flow.upsert(List.of(row("ST-20",7)),"day.csv");throw new java.io.IOException("bad later row");})).isInstanceOf(java.io.IOException.class);
        assertThat(jdbc.queryForObject("SELECT start_station_id FROM station_flow_5m",String.class)).isEqualTo("ST-10");assertThat(flow.wasImported("day.csv","a")).isTrue();
    }
    @Test void emptyProviderRevisionPreservesPriorSuccessfulRowsAndChecksum()throws Exception {
        flow.replaceFile("day.csv","a",()->{flow.upsert(List.of(row("ST-10",1)),"day.csv");return 1;});
        flow.recordEmpty("day.csv","b");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM station_flow_5m",Integer.class)).isEqualTo(1);
        assertThat(flow.wasImported("day.csv","a")).isTrue();assertThat(flow.wasImported("day.csv","b")).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM collection_run WHERE source_key='od:day.csv'",String.class)).isEqualTo("EMPTY");
    }
    UsageRow usageRow(String number,String name,long count){return new UsageRow("OA-15248","month",LocalDate.of(2024,7,1),-1,number,name,count,BigDecimal.TEN,BigDecimal.TEN);}
    @Test void repeatedUsageImportsReplaceTotalsAndFailuresPreserveDataAndManifest(){
        usage.replace("csv:month.csv",List.of(usageRow("108","대여소",12)),2,"a");
        usage.replace("csv:month.csv",List.of(usageRow("108","대여소",12)),2,"a");
        assertThat(jdbc.queryForObject("SELECT SUM(rent_count) FROM station_usage",Long.class)).isEqualTo(12);
        assertThat(usage.imported("csv:month.csv","a")).isTrue();
        assertThatThrownBy(()->usage.replace("csv:month.csv",List.of(usageRow("109","x".repeat(300),100)),1,"b")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT SUM(rent_count) FROM station_usage",Long.class)).isEqualTo(12);assertThat(usage.imported("csv:month.csv","a")).isTrue();
    }
}
