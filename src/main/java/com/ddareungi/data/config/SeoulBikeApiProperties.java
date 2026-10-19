package com.ddareungi.data.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("seoul-bike.api")
public record SeoulBikeApiProperties(
        @NotBlank String baseUrl,
        @NotBlank String key,
        int pageSize,
        int maxPages,
        @NotBlank String realtimeService,
        @NotBlank String masterService
) {}
