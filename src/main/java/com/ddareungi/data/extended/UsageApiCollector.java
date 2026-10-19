package com.ddareungi.data.extended;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

@Service
public class UsageApiCollector {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(UsageApiCollector.class);
    private final SeoulDatasetClient client; private final UsageWriter writer;
    public UsageApiCollector(SeoulDatasetClient client,UsageWriter writer){this.client=client;this.writer=writer;}
    public int collect(String kind,LocalDate date,int hour) {
        String service,envelope,suffix;
        String month=date.format(DateTimeFormatter.ofPattern("yyyyMM")),day=date.format(DateTimeFormatter.BASIC_ISO_DATE);
        switch(kind){
            case "station-month" -> {service="tbCycleStationUseMonthInfo";envelope="stationUseMonthInfo";suffix="/"+month;}
            case "month" -> {service="tbCycleRentUseMonthInfo";envelope="cycleRentUseMonthInfo";suffix="/"+month;}
            case "day" -> {service="tbCycleRentUseDayInfo";envelope="cycleRentUseDayInfo";suffix="/"+day;}
            case "hour" -> {if(hour<0||hour>23)throw new IllegalArgumentException("INVALID_HOUR");service="tbCycleRentUseTimeInfo";envelope="cycleRentUseTimeInfo";suffix="/"+day+"/"+hour;}
            default -> throw new IllegalArgumentException("UNKNOWN_DATASET");
        }
        String source="api:"+kind+suffix;
        try {
            var raw=client.fetch(service,envelope,suffix);
            if(raw.isEmpty()){writer.record(source,"EMPTY",0,0,null);return 0;}
            var aggregate=new LinkedHashMap<String,UsageRow>();
            for(var item:raw){var row=map(kind,date,hour,item);aggregate.merge(row.key(),row,UsageRow::plus);}
            writer.replace(source,aggregate.values().stream().toList(),raw.size(),null);
            log.info("{}: 원본 {}행 → 대여소 집계 {}행 저장",source,raw.size(),aggregate.size());
            return aggregate.size();
        } catch(Exception exception){writer.record(source,"FAILED",0,0,exception.getClass().getSimpleName());throw new IllegalStateException("통계 수집 실패: "+kind);}
    }
    public static UsageRow map(String kind,LocalDate requested,int hour,JsonNode item) {
        boolean stationMonth=kind.equals("station-month"),monthly=stationMonth||kind.equals("month");
        String period=item.path(stationMonth?"statMn":monthly?"RENT_NM":"RENT_DT").asString("");
        LocalDate date=monthly?java.time.YearMonth.parse(period,DateTimeFormatter.ofPattern("yyyyMM")).atDay(1):
            LocalDate.parse(period,period.contains("-")?DateTimeFormatter.ISO_LOCAL_DATE:DateTimeFormatter.BASIC_ISO_DATE);
        if(!date.equals(monthly?requested.withDayOfMonth(1):requested))throw new IllegalArgumentException("WRONG_PERIOD");
        String name=item.path(stationMonth?"stationName":monthly?"STATION_NAME":"RENT_NM").asString("");
        String number;
        if(stationMonth){var matcher=Pattern.compile("^\\s*([0-9]+)\\.").matcher(name);if(!matcher.find())throw new IllegalArgumentException("MISSING_STATION_NUMBER");number=StationInfoCollector.number(matcher.group(1));}
        else number=StationInfoCollector.number(item.path(monthly?"STATION_NO":"RENT_ID").asString(""));
        int observedHour=kind.equals("hour")?Integer.parseInt(item.path("RENT_HR").asString("-1")):-1;
        if(kind.equals("hour")&&observedHour!=hour)throw new IllegalArgumentException("WRONG_HOUR");
        long count=new BigDecimal(item.path(stationMonth?"rentCnt":"USE_CNT").asString("").replace(",","")).longValueExact();
        return new UsageRow(stationMonth?"OA-15249":monthly?"OA-15248":kind.equals("day")?"OA-15246":"OA-15245",
            monthly?"month":kind,date,observedHour,number,name,count,stationMonth?null:metric(item,"MOVE_TIME"),stationMonth?null:metric(item,"MOVE_METER"));
    }
    private static BigDecimal metric(JsonNode item,String field){String value=item.path(field).asString("");return value.isBlank()?null:new BigDecimal(value.replace(",",""));}
}
