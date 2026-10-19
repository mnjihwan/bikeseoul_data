package com.ddareungi.data.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("collector.flow-import")
public record FlowCsvProperties(
        @NotBlank String inputDirectory,
        @NotBlank String charset,
        @Min(1) int batchSize
) {}
