package com.ddareungi.data.station.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StationCollectionScheduler {
    private static final Logger log = LoggerFactory.getLogger(StationCollectionScheduler.class);
    private final StationSnapshotService service;

    public StationCollectionScheduler(StationSnapshotService service) { this.service = service; }

    @Scheduled(cron = "${collector.schedule.cron:0 */5 * * * *}", zone = "Asia/Seoul")
    public void collectEveryFiveMinutes() {
        try {
            log.info("따릉이 대여소 스냅샷 {}건 수집 완료", service.collect());
        } catch (Exception exception) {
            log.error("따릉이 대여소 스냅샷 수집 실패 ({}). API 인증과 DB 연결을 확인하세요.", exception.getClass().getSimpleName());
        }
    }
}
