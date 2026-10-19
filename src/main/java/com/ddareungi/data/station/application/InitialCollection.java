package com.ddareungi.data.station.application;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "collector.collect-on-startup", havingValue = "true", matchIfMissing = true)
public class InitialCollection {
    private static final Logger log = LoggerFactory.getLogger(InitialCollection.class);
    private final StationMasterSyncScheduler master;
    private final StationSnapshotService snapshot;
    public InitialCollection(StationMasterSyncScheduler master, StationSnapshotService snapshot) {
        this.master = master; this.snapshot = snapshot;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void collect() {
        try { master.synchronize(); }
        catch (Exception exception) { log.error("초기 마스터 수집 실패 ({}). API 키와 연결을 확인하세요.", exception.getClass().getSimpleName()); }
        try { log.info("초기 실시간 스냅샷 {}건 저장 완료", snapshot.collect()); }
        catch (Exception exception) { log.error("초기 스냅샷 수집 실패 ({}). API 키와 연결을 확인하세요.", exception.getClass().getSimpleName()); }
    }
}
