package com.ddareungi.data.station.infrastructure;

import com.ddareungi.data.config.SeoulBikeApiProperties;
import com.ddareungi.data.station.domain.StationMaster;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class SeoulBikeMasterApiClient {
    private static final String ENVELOPE = "bikeStationMaster";
    private final SeoulBikeApiProperties properties;
    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public SeoulBikeMasterApiClient(SeoulBikeApiProperties properties, RestClient.Builder builder, JsonMapper jsonMapper) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.jsonMapper = jsonMapper;
    }

    public List<StationMaster> fetchAll() {
        List<StationMaster> stations = new ArrayList<>();
        for (int page = 0; page < properties.maxPages(); page++) {
            int start = page * properties.pageSize() + 1;
            int end = start + properties.pageSize() - 1;
            String body = restClient.get()
                    .uri("/{key}/json/{service}/{start}/{end}", properties.key(), properties.masterService(), start, end)
                    .retrieve()
                    .body(String.class);
            List<StationMaster> pageRows = parse(body);
            stations.addAll(pageRows);
            if (pageRows.size() < properties.pageSize()) break;
        }
        return stations;
    }

    List<StationMaster> parse(String body) {
        try {
            JsonNode root = jsonMapper.readTree(body);
            JsonNode envelope = root.path(ENVELOPE);
            String code = envelope.path("RESULT").path("CODE").asString();
            if (!"INFO-000".equals(code)) {
                throw new IllegalStateException("마스터 API 오류: " + code + " "
                        + envelope.path("RESULT").path("MESSAGE").asString());
            }
            List<StationMaster> result = new ArrayList<>();
            for (JsonNode row : envelope.path("row")) {
                BigDecimal latitude = decimal(row, "LAT");
                BigDecimal longitude = decimal(row, "LOT");
                if (latitude.signum() == 0 && longitude.signum() == 0) {
                    latitude = null;
                    longitude = null;
                }
                result.add(new StationMaster(row.path("RNTLS_ID").asString(), row.path("ADDR1").asString(),
                        row.path("ADDR2").asString(), latitude, longitude));
            }
            return result;
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("서울시 따릉이 마스터 API 응답을 해석하지 못했습니다.", exception);
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.path(field).asString("0"));
    }
}
