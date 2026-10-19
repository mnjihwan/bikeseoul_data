package com.ddareungi.data.extended;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
import java.util.List;

@Validated
@ConfigurationProperties("collector.extended")
public record ExtendedProperties(boolean enabled,boolean downloadEnabled,@Min(1) int downloadMaxFiles,@Min(5) int downloadLookbackDays,
    @Min(1024) long maxDownloadBytes,@Min(1024) long maxExpandedBytes,@NotBlank String usageDirectory,
    List<String> usageKinds,@Min(1) int usageLagDays,@Min(1) int monthlyLookback,String backfillTasks,boolean once) {}
