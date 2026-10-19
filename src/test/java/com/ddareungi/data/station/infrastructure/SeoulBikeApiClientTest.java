package com.ddareungi.data.station.infrastructure;

import com.ddareungi.data.config.SeoulBikeApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SeoulBikeApiClientTest {
    private final SeoulBikeApiClient client = new SeoulBikeApiClient(
            new SeoulBikeApiProperties("http://localhost", "sample", 1000, 5,
                    "bikeList", "bikeStationMaster"),
            RestClient.builder(), JsonMapper.builder().build());

    @Test
    void parsesOfficialRealtimeEnvelope() {
        String body = """
                {"rentBikeStatus":{"list_total_count":1,"RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다."},
                "row":[{"rackTotCnt":"15","stationName":"102. 망원역 1번출구 앞",
                "parkingBikeTotCnt":"9","shared":"60","stationLatitude":"37.55564880",
                "stationLongitude":"126.91062927","stationId":"ST-4"}]}}
                """;

        var rows = client.parse(body);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().stationId()).isEqualTo("ST-4");
        assertThat(rows.getFirst().parkingBikeTotalCount()).isEqualTo(9);
        assertThat(rows.getFirst().latitude()).isEqualByComparingTo("37.55564880");
    }

    @org.junit.jupiter.api.Test void rejectsUnavailableCountsInsteadOfInventingZero() {
        for (String field : java.util.List.of("", "\"parkingBikeTotCnt\":null,", "\"parkingBikeTotCnt\":\"\",",
                "\"parkingBikeTotCnt\":\"unknown\",", "\"parkingBikeTotCnt\":-1,", "\"parkingBikeTotCnt\":1.5,")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> readCounts(field))
                    .as(field).isInstanceOf(IllegalStateException.class);
        }
    }

    @org.junit.jupiter.api.Test void preservesActualZeroAndNumericCounts() {
        org.assertj.core.api.Assertions.assertThat(readCounts("\"parkingBikeTotCnt\":\"0\"," )).isZero();
        org.assertj.core.api.Assertions.assertThat(readCounts("\"parkingBikeTotCnt\":0," )).isZero();
        org.assertj.core.api.Assertions.assertThat(readCounts("\"parkingBikeTotCnt\":9," )).isEqualTo(9);
    }

    private String body(String field) {
        return "{\"rentBikeStatus\":{\"RESULT\":{\"CODE\":\"INFO-000\"},\"row\":[{" + field +
                "\"stationId\":\"ST-1\",\"stationName\":\"강남역\",\"rackTotCnt\":\"20\",\"shared\":\"0\",\"stationLatitude\":\"37.5\",\"stationLongitude\":\"127\"}]}}";
    }

    private int readCounts(String field) { return client.parse(body(field)).getFirst().parkingBikeTotalCount(); }
}
