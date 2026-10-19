package com.ddareungi.data.station.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StationSnapshot(
        String stationId,
        String stationName,
        int rackTotalCount,
        int parkingBikeTotalCount,
        BigDecimal shared,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime stationDateTime
) {}
