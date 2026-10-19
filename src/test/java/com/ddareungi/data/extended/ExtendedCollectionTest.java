package com.ddareungi.data.extended;
import com.ddareungi.data.config.SeoulBikeApiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;
import org.apache.commons.csv.CSVFormat;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import java.util.List;
import java.time.LocalDate;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ExtendedCollectionTest {
    final JsonMapper mapper=JsonMapper.builder().build();
    @TempDir Path directory;
    SeoulDatasetClient client(RestClient.Builder builder){return new SeoulDatasetClient(new SeoulBikeApiProperties("http://localhost","test-secret",1000,5,"bikeList","bikeStationMaster"),builder,mapper);}
    @Test void understandsActualEnvelopeNamesAndNoDataResponses()throws Exception {
        var client=client(RestClient.builder());
        assertThat(client.parse("{\"stationInfo\":{\"list_total_count\":\"1\",\"RESULT\":{\"CODE\":\"INFO-000\"},\"row\":[{}]}}","stationInfo").rows()).hasSize(1);
        assertThat(client.parse("{\"RESULT\":{\"CODE\":\"INFO-200\"}}","stationUseMonthInfo").empty()).isTrue();
        assertThatThrownBy(()->client.parse("{\"RESULT\":{\"CODE\":\"ERROR-301\",\"MESSAGE\":\"test-secret\"}}","stationInfo"))
            .hasMessageNotContaining("test-secret");
    }
    @Test void paginatesBeyondOneThousandWithoutSavingIncompleteResults() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var client=client(builder);
        String rows=String.join(",",java.util.Collections.nCopies(1000,"{}"));
        server.expect(requestTo("http://localhost/test-secret/json/service/1/1000/202609")).andRespond(withSuccess("{\"envelope\":{\"list_total_count\":1001,\"RESULT\":{\"CODE\":\"INFO-000\"},\"row\":["+rows+"]}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/test-secret/json/service/1001/2000/202609")).andRespond(withSuccess("{\"RESULT\":{\"CODE\":\"INFO-200\"}}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->client.fetch("service","envelope","/202609")).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("test-secret");server.verify();
    }
    @Test void returnsEveryPageOnSuccessfulPagination() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var client=client(builder);
        String rows=String.join(",",java.util.Collections.nCopies(1000,"{}"));
        server.expect(anything()).andRespond(withSuccess("{\"envelope\":{\"list_total_count\":1001,\"RESULT\":{\"CODE\":\"INFO-000\"},\"row\":["+rows+"]}}",MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess("{\"envelope\":{\"list_total_count\":1001,\"RESULT\":{\"CODE\":\"INFO-000\"},\"row\":[{}]}}",MediaType.APPLICATION_JSON));
        assertThat(client.fetch("service","envelope","/202609")).hasSize(1001);server.verify();
    }
    @Test void providerHttpFailuresDoNotExposeKeyOrErrorBody() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("test-secret"));
        assertThatThrownBy(()->client(builder).fetch("service","envelope",""))
            .hasMessageNotContaining("test-secret").hasCause(null);
    }
    @Test void mapsMonthlyStationCountsAndNormalizesOfficialNumbers()throws Exception {
        var row=UsageApiCollector.map("station-month",LocalDate.of(2026,9,1),-1,mapper.readTree("{\"statMn\":\"202609\",\"stationName\":\"108. 서교동 사거리\",\"rentCnt\":\"1,431\"}"));
        assertThat(row.number()).isEqualTo("108");assertThat(row.count()).isEqualTo(1431);assertThat(row.minutes()).isNull();
        assertThat(StationInfoCollector.number("00108")).isEqualTo("108");
        assertThatThrownBy(()->StationInfoCollector.number("ST-10")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingRackCountsRemainUnknown(){assertThat(StationInfoCollector.optionalCount(null)).isNull();assertThat(StationInfoCollector.optionalCount("")).isNull();assertThat(StationInfoCollector.optionalCount("12.0")).isEqualTo(12);}
    @Test void mapsActualHourlyAndMonthlyFieldNamesAndRejectsWrongPeriods()throws Exception {
        var hour=mapper.readTree("{\"RENT_DT\":\"2026-10-06\",\"RENT_HR\":\"9\",\"RENT_ID\":\"00770\",\"RENT_NM\":\"770. 목동역\",\"USE_CNT\":\"2\",\"MOVE_TIME\":\"17\",\"MOVE_METER\":\"1129.66\"}");
        assertThat(UsageApiCollector.map("hour",LocalDate.of(2026,10,6),9,hour).meters()).isEqualByComparingTo("1129.66");
        assertThatThrownBy(()->UsageApiCollector.map("hour",LocalDate.of(2026,10,7),9,hour)).hasMessage("WRONG_PERIOD");
        assertThatThrownBy(()->UsageApiCollector.map("hour",LocalDate.of(2026,10,6),10,hour)).hasMessage("WRONG_HOUR");
        var month=mapper.readTree("{\"RENT_NM\":\"202606\",\"STATION_NO\":\"00731\",\"STATION_NAME\":\"731. 도로환경관리센터\",\"USE_CNT\":\"1\",\"MOVE_TIME\":\"26\",\"MOVE_METER\":\"2583.88\"}");
        assertThat(UsageApiCollector.map("month",LocalDate.of(2026,6,1),-1,month).number()).isEqualTo("731");
    }
    @Test void invalidApiRowsDoNotReachDatabaseReplacement()throws Exception {
        var client=mock(SeoulDatasetClient.class);var writer=mock(UsageWriter.class);
        when(client.fetch(anyString(),anyString(),anyString())).thenReturn(List.of(mapper.readTree("{\"statMn\":\"202608\",\"stationName\":\"108. 대여소\",\"rentCnt\":\"1\"}")));
        assertThatThrownBy(()->new UsageApiCollector(client,writer).collect("station-month",LocalDate.of(2026,9,1),-1)).isInstanceOf(IllegalStateException.class);
        verify(writer,never()).replace(anyString(),anyList(),anyLong(),any());
        verify(writer).record("api:station-month/202609","FAILED",0,0,"IllegalArgumentException");
    }
    @Test void noDataDoesNotDeletePreviousSuccessfulData() {
        var client=mock(SeoulDatasetClient.class);var writer=mock(UsageWriter.class);when(client.fetch(anyString(),anyString(),anyString())).thenReturn(List.of());
        assertThat(new UsageApiCollector(client,writer).collect("day",LocalDate.of(2026,10,6),-1)).isZero();
        verify(writer,never()).replace(anyString(),anyList(),anyLong(),any());verify(writer).record("api:day/20261006","EMPTY",0,0,null);
    }
    @Test void monthlyCsvAggregatesCategoriesWithoutPersistingDemographics()throws Exception {
        var record=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(new StringReader("대여일자,대여소번호,대여소명,이용건수,이용시간(분),이동거리(M)\n202407,00108,서교동 사거리,2,15,1129.66\n")).getRecords().getFirst();
        var row=UsageCsvImporter.map(record);assertThat(row.date()).isEqualTo(LocalDate.of(2024,7,1));assertThat(row.number()).isEqualTo("108");
        assertThat(row.plus(row).count()).isEqualTo(4);assertThat(row.plus(row).meters()).isEqualByComparingTo("2259.32");
        assertThatThrownBy(()->new UsageRow("x","day",row.date(),-1,"108","대여소",-1,BigDecimal.ZERO,BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingUsageMetricsRemainUnknownAndMissingRentalCountsAreRejected()throws Exception {
        var item=mapper.readTree("{\"RENT_DT\":\"2026-10-06\",\"RENT_ID\":\"00108\",\"RENT_NM\":\"108. 대여소\",\"USE_CNT\":\"1\",\"MOVE_TIME\":\"\"}");
        var row=UsageApiCollector.map("day",LocalDate.of(2026,10,6),-1,item);
        assertThat(row.minutes()).isNull();assertThat(row.meters()).isNull();assertThat(row.count()).isEqualTo(1);
        var missing=mapper.readTree("{\"RENT_DT\":\"2026-10-06\",\"RENT_ID\":\"00108\",\"RENT_NM\":\"108. 대여소\"}");
        assertThatThrownBy(()->UsageApiCollector.map("day",LocalDate.of(2026,10,6),-1,missing)).isInstanceOf(NumberFormatException.class);
    }
    @Test void discoversOnlyPublishedDailyOdFilesWithTheirRealSequence() {
        var files=OdDownloader.discover("<span title=\"tpss_bcycl_od_statnhm_20261006.zip\" onclick=\"javascript:downloadFile('261006');\">x</span>"+
            "<span title=\"tpss_bcycl_od_statnhm_202609.zip\" onclick=\"javascript:downloadFile('2609');\">x</span>");
        assertThat(files).hasSize(1);assertThat(files.getFirst().sequence()).isEqualTo("261006");assertThat(files.getFirst().date()).isEqualTo(LocalDate.of(2026,10,6));
    }
    Path zip(String... names)throws Exception {
        Path file=directory.resolve("input.zip");try(var output=new ZipOutputStream(Files.newOutputStream(file))){for(String name:names){output.putNextEntry(new ZipEntry(name));output.write("1234567890".getBytes());output.closeEntry();}}return file;
    }
    @Test void zipTraversalOrOversizeCannotLeavePartialCsvFiles()throws Exception {
        Path out=directory.resolve("out");
        var archive=zip("tpss_bcycl_od_statnhm_20261006.csv","../escape.csv");
        assertThatThrownBy(()->DataFiles.extractOdZip(archive,out,100)).hasMessage("UNEXPECTED_ZIP_ENTRY");
        assertThat(Files.list(out).toList()).isEmpty();assertThat(directory.resolve("escape.csv")).doesNotExist();
        var valid=zip("tpss_bcycl_od_statnhm_20261006.csv");
        assertThatThrownBy(()->DataFiles.extractOdZip(valid,out,5)).hasMessage("FILE_SIZE_LIMIT");assertThat(Files.list(out).toList()).isEmpty();
    }
    @Test void validZipIsExtractedToExpectedCsv()throws Exception {
        Path out=directory.resolve("out");assertThat(DataFiles.extractOdZip(zip("tpss_bcycl_od_statnhm_20261006.csv"),out,100)).isEqualTo(1);
        assertThat(out.resolve("tpss_bcycl_od_statnhm_20261006.csv")).hasContent("1234567890");
    }
}
