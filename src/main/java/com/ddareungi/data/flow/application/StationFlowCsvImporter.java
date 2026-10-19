package com.ddareungi.data.flow.application;

import com.ddareungi.data.config.FlowCsvProperties;
import com.ddareungi.data.flow.domain.StationFlow;
import com.ddareungi.data.flow.infrastructure.StationFlowWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Component
public class StationFlowCsvImporter {
    private static final Logger log = LoggerFactory.getLogger(StationFlowCsvImporter.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .get();

    private final FlowCsvProperties properties;
    private final StationFlowWriter writer;

    public StationFlowCsvImporter(FlowCsvProperties properties, StationFlowWriter writer) {
        this.properties = properties;
        this.writer = writer;
    }

    public synchronized void importDirectory() throws Exception {
        Path directory = Path.of(properties.inputDirectory()).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            log.warn("OA-21229 CSV 입력 디렉터리가 없습니다: {}", directory);
            return;
        }
        try (var paths = Files.list(directory)) {
            int failures=0;
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().matches("tpss_bcycl_od_statnhm_.*\\.csv"))
                    .sorted(Comparator.naturalOrder()).toList()) {
                try{importFile(path);}catch(Exception error){failures++;log.error("OD 파일 {} 적재 실패 ({})",path.getFileName(),error.getClass().getSimpleName());}
            }
            if(failures>0)throw new IllegalStateException(failures+"개 OD 파일 적재 실패");
        }
    }

    public void importFile(Path path) throws Exception {
        String sourceFile = path.getFileName().toString();
        String checksum = sha256(path);
        if (writer.wasImported(sourceFile, checksum)) {
            log.info("이미 import한 CSV를 건너뜁니다: {}", sourceFile);
            return;
        }

        try(var reader=com.ddareungi.data.extended.DataFiles.csvReader(path,Charset.forName(properties.charset()));
            var parser=FORMAT.parse(reader)){
            if(!parser.getHeaderMap().keySet().containsAll(List.of("기준_날짜","집계_기준","기준_시간대","시작_대여소_ID","시작_대여소명","종료_대여소_ID","종료_대여소명","전체_건수","전체_이용_분","전체_이용_거리")))
                throw new IllegalArgumentException("INVALID_OD_HEADERS");
            if(!parser.iterator().hasNext()){
                writer.recordEmpty(sourceFile,checksum);
                log.warn("공식 OD 원본에 데이터 행이 없어 제외합니다 (EMPTY): {}",sourceFile);
                return;
            }
        }

        long rowCount = writer.replaceFile(sourceFile,checksum,()->readBatches(path,sourceFile));
        log.info("OA-21229 CSV import 완료: file={}, rows={}", sourceFile, rowCount);
    }

    private long readBatches(Path path,String sourceFile) throws Exception {
        long rowCount=0;
        List<StationFlow> batch = new ArrayList<>(properties.batchSize());
        try (Reader reader = com.ddareungi.data.extended.DataFiles.csvReader(path, Charset.forName(properties.charset()));
             CSVParser parser = FORMAT.parse(reader)) {
            for (CSVRecord csv : parser) {
                batch.add(toFlow(csv));
                rowCount++;
                if (batch.size() == properties.batchSize()) {
                    writer.upsert(batch, sourceFile);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) writer.upsert(batch, sourceFile);
        return rowCount;
    }

    StationFlow toFlow(CSVRecord csv) {
        var row = new StationFlow(
                LocalDate.parse(csv.get("기준_날짜"), DATE),
                csv.get("집계_기준"),
                LocalTime.parse(csv.get("기준_시간대"), TIME),
                csv.get("시작_대여소_ID"),
                csv.get("시작_대여소명"),
                csv.get("종료_대여소_ID"),
                csv.get("종료_대여소명"),
                Integer.parseInt(csv.get("전체_건수")),
                optionalMetric(csv.get("전체_이용_분")),
                optionalMetric(csv.get("전체_이용_거리")));
        if(!List.of("출발시간","도착시간").contains(row.aggregationBasis()) || row.timeSlot().getMinute()%5!=0 ||
           row.rideCount()<0 || row.usageMinutes()!=null&&row.usageMinutes()<0 || row.distanceMeters()!=null&&row.distanceMeters()<0 ||
           !row.startStationId().matches("ST-[0-9]+") || !row.endStationId().matches("ST-[0-9]+|X"))
            throw new IllegalArgumentException("INVALID_OD_ROW");
        return row;
    }
    private static Long optionalMetric(String value){return value.isBlank()?null:new java.math.BigDecimal(value.replace(",","")).longValueExact();}

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path);
             DigestInputStream stream = new DigestInputStream(input, digest)) {
            stream.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
