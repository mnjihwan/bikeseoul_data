package com.ddareungi.data.station.infrastructure;

import com.ddareungi.data.config.SeoulBikeApiProperties;
import com.ddareungi.data.station.domain.StationSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class SeoulBikeApiClient {
    private final SeoulBikeApiProperties properties;
    private final RestClient restClient;
    private final JsonMapper objectMapper;
    private final Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));

    public SeoulBikeApiClient(SeoulBikeApiProperties properties, RestClient.Builder builder, JsonMapper objectMapper) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.objectMapper = objectMapper;
    }

    public List<StationSnapshot> fetchAll() {
        List<StationSnapshot> snapshots = new ArrayList<>();
        for (int page = 0; page < properties.maxPages(); page++) {
            int start = page * properties.pageSize() + 1;
            int end = start + properties.pageSize() - 1;
            String body = restClient.get()
                    .uri("/{key}/json/{service}/{start}/{end}", properties.key(), properties.realtimeService(), start, end)
                    .retrieve()
                    .body(String.class);
            List<StationSnapshot> pageRows = parse(body);
            snapshots.addAll(pageRows);
            if (pageRows.size() < properties.pageSize()) break;
        }
        return snapshots;
    }

    List<StationSnapshot> parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode envelope = root.path("rentBikeStatus");
            String code = envelope.path("RESULT").path("CODE").asString(
                    root.path("RESULT").path("CODE").asString(""));
            if (!"INFO-000".equals(code)) {
                throw new IllegalStateException("실시간 API 오류: " + code + " "
                        + envelope.path("RESULT").path("MESSAGE").asString(
                        root.path("RESULT").path("MESSAGE").asString("")));
            }
            JsonNode rows = envelope.path("row");
            if (!rows.isArray()) return List.of();
            LocalDateTime observed = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
            LocalDateTime collectedSlot = observed.withMinute(observed.getMinute() / 5 * 5);
            List<StationSnapshot> result = new ArrayList<>();
            for (JsonNode row : rows) {
                result.add(new StationSnapshot(
                        row.path("stationId").asString(), row.path("stationName").asString(),
                        row.path("rackTotCnt").asInt(), availableBikes(row),
                        decimal(row, "shared"), decimal(row, "stationLatitude"),
                        decimal(row, "stationLongitude"), collectedSlot));
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("서울시 따릉이 API 응답을 해석하지 못했습니다.", exception);
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = node.path(field).asString("0").replace("%", "");
        return new BigDecimal(value.isBlank() ? "0" : value);
    }

    private static int availableBikes(JsonNode row) {
        String value = row.path("parkingBikeTotCnt").asString("");
        if (!value.matches("[0-9]+")) throw new IllegalStateException("Missing or invalid bike count");
        return Integer.parseInt(value);
    }
}
