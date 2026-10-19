package com.ddareungi.data.station.domain;

import java.math.BigDecimal;

public record StationMaster(
        String stationId,
        String address1,
        String address2,
        BigDecimal latitude,
        BigDecimal longitude
) {}
