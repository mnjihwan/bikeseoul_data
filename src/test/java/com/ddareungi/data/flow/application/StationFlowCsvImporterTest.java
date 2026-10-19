package com.ddareungi.data.flow.application;

import com.ddareungi.data.config.FlowCsvProperties;
import com.ddareungi.data.flow.infrastructure.StationFlowWriter;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StationFlowCsvImporterTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void recordsOfficialHeaderOnlyFileAsEmptyWithoutReplacingData()throws Exception {
        var path=directory.resolve("empty.csv");
        java.nio.file.Files.writeString(path,"기준_날짜,집계_기준,기준_시간대,시작_대여소_ID,시작_대여소명,종료_대여소_ID,종료_대여소명,전체_건수,전체_이용_분,전체_이용_거리\n",java.nio.charset.StandardCharsets.UTF_8);
        var writer=mock(StationFlowWriter.class);
        new StationFlowCsvImporter(new FlowCsvProperties(".","MS949",1000),writer).importFile(path);
        org.mockito.Mockito.verify(writer).recordEmpty(org.mockito.ArgumentMatchers.eq("empty.csv"),org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(writer,org.mockito.Mockito.never()).replaceFile(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
    }
    @Test void readsBothOfficialEncodingsAndOptionalUtf8Bom()throws Exception {
        String content="기준_날짜,집계_기준,기준_시간대,시작_대여소_ID,시작_대여소명,종료_대여소_ID,종료_대여소명,전체_건수,전체_이용_분,전체_이용_거리\n20260918,출발시간,0000,ST-10,출발,ST-20,도착,1,,\n";
        var importer=new StationFlowCsvImporter(new FlowCsvProperties(".","MS949",1000),mock(StationFlowWriter.class));
        for(String encoding:java.util.List.of("MS949","UTF-8","UTF-8-BOM")){
            var path=directory.resolve(encoding+".csv");
            java.nio.file.Files.writeString(path,(encoding.endsWith("BOM")?"\ufeff":"")+content,java.nio.charset.Charset.forName(encoding.startsWith("UTF")?"UTF-8":"MS949"));
            try(var reader=com.ddareungi.data.extended.DataFiles.csvReader(path,java.nio.charset.Charset.forName("MS949"));
                var csv=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)){
                assertThat(importer.toFlow(csv.getRecords().getFirst()).startStationName()).isEqualTo("출발");
            }
        }
    }
    @Test void doesNotSilentlyReplaceMalformedBodyCharacters()throws Exception {
        var path=directory.resolve("malformed.csv");
        var bytes=new java.io.ByteArrayOutputStream();bytes.write("기준_날짜\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));bytes.write(0xff);
        java.nio.file.Files.write(path,bytes.toByteArray());
        org.assertj.core.api.Assertions.assertThatThrownBy(()->{
            try(var reader=com.ddareungi.data.extended.DataFiles.csvReader(path,java.nio.charset.Charset.forName("MS949"))){while(reader.read()!=-1){}}
        }).isInstanceOf(java.nio.charset.MalformedInputException.class);
    }
    @Test
    void mapsOfficialOa21229Headers() throws Exception {
        String csv = """
                "기준_날짜","집계_기준","기준_시간대","시작_대여소_ID","시작_대여소명","종료_대여소_ID","종료_대여소명","전체_건수","전체_이용_분","전체_이용_거리"
                "20260903","출발시간","0000","ST-1005","신정1동_007_1","ST-714","은천동_007_1","1","51","11970"
                """;
        var record = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()
                .parse(new StringReader(csv)).getRecords().getFirst();
        var importer = new StationFlowCsvImporter(
                new FlowCsvProperties(".", "MS949", 1000), mock(StationFlowWriter.class));

        var flow = importer.toFlow(record);

        assertThat(flow.serviceDate()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(flow.timeSlot()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(flow.startStationId()).isEqualTo("ST-1005");
        assertThat(flow.usageMinutes()).isEqualTo(51L);
        assertThat(flow.distanceMeters()).isEqualTo(11970L);
    }
    @Test void preservesMissingMetricsWithoutInventingZero()throws Exception {
        var record=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(new StringReader("기준_날짜,집계_기준,기준_시간대,시작_대여소_ID,시작_대여소명,종료_대여소_ID,종료_대여소명,전체_건수,전체_이용_분,전체_이용_거리\n20261006,출발시간,0000,ST-10,출발,ST-20,도착,1,,\n")).getRecords().getFirst();
        var flow=new StationFlowCsvImporter(new FlowCsvProperties(".","MS949",1000),mock(StationFlowWriter.class)).toFlow(record);
        assertThat(flow.rideCount()).isEqualTo(1);assertThat(flow.usageMinutes()).isNull();assertThat(flow.distanceMeters()).isNull();
    }
    @Test void retainsOriginalXDestinationWithoutTurningItIntoARealStation()throws Exception {
        var record=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(new StringReader("기준_날짜,집계_기준,기준_시간대,시작_대여소_ID,시작_대여소명,종료_대여소_ID,종료_대여소명,전체_건수,전체_이용_분,전체_이용_거리\n20261006,출발시간,0000,ST-10,출발,X,미확인,1,,\n")).getRecords().getFirst();
        var flow=new StationFlowCsvImporter(new FlowCsvProperties(".","MS949",1000),mock(StationFlowWriter.class)).toFlow(record);
        assertThat(flow.endStationId()).isEqualTo("X");assertThat(flow.rideCount()).isEqualTo(1);
    }
}
