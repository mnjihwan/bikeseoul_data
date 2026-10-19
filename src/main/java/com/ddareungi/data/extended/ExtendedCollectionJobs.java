package com.ddareungi.data.extended;
import com.ddareungi.data.config.FlowCsvProperties;
import com.ddareungi.data.flow.application.StationFlowCsvImporter;
import com.ddareungi.data.station.application.StationSnapshotService;
import com.ddareungi.data.station.application.StationMasterSyncScheduler;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.time.*;

@Component
public class ExtendedCollectionJobs {
    private static final Logger log=LoggerFactory.getLogger(ExtendedCollectionJobs.class);
    private final ExtendedProperties properties;private final FlowCsvProperties flowProperties;
    private final OdDownloader downloader;private final StationFlowCsvImporter flow;private final UsageCsvImporter csv;
    private final StationInfoCollector info;private final UsageApiCollector usage;
    private final ConfigurableApplicationContext context;
    private int failures;
    private final StationSnapshotService snapshot;private final StationMasterSyncScheduler master;
    public ExtendedCollectionJobs(ExtendedProperties properties,FlowCsvProperties flowProperties,OdDownloader downloader,StationFlowCsvImporter flow,
        UsageCsvImporter csv,StationInfoCollector info,UsageApiCollector usage,ConfigurableApplicationContext context,StationSnapshotService snapshot,StationMasterSyncScheduler master){
        this.properties=properties;this.flowProperties=flowProperties;this.downloader=downloader;this.flow=flow;this.csv=csv;this.info=info;this.usage=usage;this.context=context;
        this.snapshot=snapshot;this.master=master;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void once(){if(properties.enabled()&&properties.once()){try{collect();if(failures>0)throw new IllegalStateException("추가 수집 중 "+failures+"개 작업 실패");}finally{context.close();}}}
    @Scheduled(initialDelay=15000,fixedDelay=Long.MAX_VALUE)
    public void startup(){if(properties.enabled()&&!properties.once())collect();}
    @Scheduled(cron="${collector.extended.cron:0 10 4 * * *}",zone="Asia/Seoul")
    public void daily(){if(properties.enabled()&&!properties.once())collect();}
    public synchronized void collect(){
        failures=0;
        if(properties.once()){safe("대여소 기본 정보",master::synchronize);safe("5분 수량 이력",()->log.info("수량 {}건 저장",snapshot.collect()));}
        safe("대여소 번호/ID 연결",()->log.info("대여소 상세 {}건 저장",info.collect()));
        if(properties.downloadEnabled())safe("OD 다운로드",()->log.info("공식 OD {}개 파일 다운로드",downloader.download()));
        safe("보유 OD ZIP",()->{
            Path directory=Path.of(flowProperties.inputDirectory());if(!Files.isDirectory(directory))return;
            try(var files=Files.list(directory)){for(Path archive:files.filter(p->p.getFileName().toString().matches("tpss_bcycl_od_statnhm_[0-9]{6}([0-9]{2})?\\.zip")).toList()){
                Path target=directory.resolve(archive.getFileName().toString().replace(".zip",".csv"));if(!Files.exists(target))DataFiles.extractOdZip(archive,directory,properties.maxExpandedBytes());
            }}
        });
        safe("OD DB 저장",flow::importDirectory);
        safe("이용통계 CSV 저장",()->log.info("CSV 대여소 집계 {}행 저장",csv.importDirectory()));
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Seoul")),date=today.minusDays(properties.usageLagDays());
        if(properties.backfillTasks()!=null&&!properties.backfillTasks().isBlank()){
            for(String task:properties.backfillTasks().split(",")){String[] parts=task.trim().split(":");safe("통계 "+parts[0],()->usage.collect(parts[0],parts[1].length()==7?YearMonth.parse(parts[1]).atDay(1):LocalDate.parse(parts[1]),parts.length==3?Integer.parseInt(parts[2]):-1));}
        }else{
            for(String kind:properties.usageKinds()){
                if(kind.equals("station-month")||kind.equals("month"))for(int offset=1;offset<=properties.monthlyLookback();offset++){var month=today.withDayOfMonth(1).minusMonths(offset);safe("통계 "+kind,()->usage.collect(kind,month,-1));}
                else if(kind.equals("day"))safe("일별 통계",()->usage.collect(kind,date,-1));
                else if(kind.equals("hour"))for(int hour=0;hour<24;hour++){int selectedHour=hour;safe("시간별 통계",()->usage.collect(kind,date,selectedHour));}
            }
        }
    }
    @FunctionalInterface private interface Operation{void run()throws Exception;}
    private void safe(String label,Operation operation){try{operation.run();}catch(Exception error){failures++;log.error("{} 실패 ({})",label,error.getClass().getSimpleName());}}
}
