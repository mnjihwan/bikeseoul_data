package com.ddareungi.data.station.infrastructure;

import com.ddareungi.data.config.SeoulBikeApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SeoulBikeMasterApiClientTest {
    private final SeoulBikeMasterApiClient client = new SeoulBikeMasterApiClient(
            new SeoulBikeApiProperties("http://localhost", "sample", 1000, 5,
                    "bikeList", "bikeStationMaster"),
            RestClient.builder(), JsonMapper.builder().build());

    @Test
    void parsesOfficialMasterEnvelopeAndDropsZeroCoordinates() {
        String body = """
                {"bikeStationMaster":{"list_total_count":2,"RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다"},
                "row":[{"RNTLS_ID":"ST-10","ADDR1":"서울특별시 마포구 양화로 93","ADDR2":"427",
                "LAT":37.552746,"LOT":126.918617},{"RNTLS_ID":"ST-1001","ADDR1":"서울특별시 양천구",
                "ADDR2":"서서울호수공원","LAT":0.0,"LOT":0.0}]}}
                """;

        var rows = client.parse(body);

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().stationId()).isEqualTo("ST-10");
        assertThat(rows.getFirst().longitude()).isEqualByComparingTo("126.918617");
        assertThat(rows.get(1).latitude()).isNull();
        assertThat(rows.get(1).longitude()).isNull();
    }
}
