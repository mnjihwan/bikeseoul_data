package com.ddareungi.data.station.application;

import com.ddareungi.data.station.infrastructure.SeoulBikeMasterApiClient;
import com.ddareungi.data.station.infrastructure.StationMasterWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StationMasterSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(StationMasterSyncScheduler.class);
    private final SeoulBikeMasterApiClient client;
    private final StationMasterWriter writer;

    public StationMasterSyncScheduler(SeoulBikeMasterApiClient client, StationMasterWriter writer) {
        this.client = client;
        this.writer = writer;
    }

    @Transactional
    @Scheduled(cron = "${collector.master.cron:0 20 3 * * *}", zone = "Asia/Seoul")
    public void synchronize() {
        var stations = client.fetchAll();
        writer.upsert(stations);
        log.info("OA-21235 대여소 마스터 {}건 동기화 완료", stations.size());
    }
}
