package com.ddareungi.data.extended;
import org.springframework.stereotype.Component;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.math.BigDecimal;

@Component
public class UsageCsvImporter {
    private final ExtendedProperties properties;private final UsageWriter writer;
    public UsageCsvImporter(ExtendedProperties properties,UsageWriter writer){this.properties=properties;this.writer=writer;}
    public synchronized int importDirectory()throws Exception {
        Path directory=Path.of(properties.usageDirectory());if(!Files.isDirectory(directory))return 0;int total=0;
        try(var paths=Files.list(directory)){for(Path file:paths.filter(Files::isRegularFile).filter(p->p.getFileName().toString().endsWith(".csv")).sorted().toList())total+=importFile(file);}
        return total;
    }
    public int importFile(Path file)throws Exception {
        String source="csv:"+file.getFileName(),checksum=DataFiles.checksum(file);
        if(writer.imported(source,checksum))return 0;
        var aggregate=new LinkedHashMap<String,UsageRow>();long count=0;
        try(var reader=DataFiles.csvReader(file,Charset.forName("MS949"));var csv=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get().parse(reader)){
            for(var record:csv){var row=map(record);aggregate.merge(row.key(),row,UsageRow::plus);count++;}
        }
        if(aggregate.isEmpty())throw new IllegalArgumentException("EMPTY_USAGE_CSV");
        writer.replace(source,aggregate.values().stream().toList(),count,checksum);
        return aggregate.size();
    }
    public static UsageRow map(CSVRecord record) {
        String dateValue=record.get("대여일자").replace("-","");
        boolean monthly=dateValue.length()==6,hourly=record.isMapped("대여시간");
        LocalDate date=monthly?YearMonth.parse(dateValue,DateTimeFormatter.ofPattern("yyyyMM")).atDay(1):LocalDate.parse(dateValue,DateTimeFormatter.BASIC_ISO_DATE);
        int hour=hourly?Integer.parseInt(record.get("대여시간")):-1;
        return new UsageRow(monthly?"OA-15248":hourly?"OA-15245":"OA-15246",monthly?"month":hourly?"hour":"day",date,hour,
            StationInfoCollector.number(record.get("대여소번호")),record.get("대여소명"),new BigDecimal(record.get("이용건수").replace(",","")).longValueExact(),
            metric(record.get("이용시간(분)")),metric(record.get("이동거리(M)")));
    }
    private static BigDecimal metric(String value){return value.isBlank()?null:new BigDecimal(value.replace(",",""));}
}
