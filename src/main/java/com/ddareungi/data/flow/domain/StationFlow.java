package com.ddareungi.data.flow.domain;

import java.time.LocalDate;
import java.time.LocalTime;

public record StationFlow(
        LocalDate serviceDate,
        String aggregationBasis,
        LocalTime timeSlot,
        String startStationId,
        String startStationName,
        String endStationId,
        String endStationName,
        int rideCount,
        Long usageMinutes,
        Long distanceMeters
) {}
