package com.ddareungi.data.extended;
import com.ddareungi.data.config.FlowCsvProperties;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class OdDownloader {
    private final FlowCsvProperties flow; private final ExtendedProperties properties;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final URI LIST=URI.create("https://data.seoul.go.kr/dataList/OA-21229/F/1/datasetView.do");
    private static final URI DOWNLOAD=URI.create("https://datafile.seoul.go.kr/bigfile/iot/inf/nio_download.do?useCache=false");
    public OdDownloader(FlowCsvProperties flow,ExtendedProperties properties){this.flow=flow;this.properties=properties;}
    public static List<RemoteFile> discover(String html) {
        var pattern=Pattern.compile("<span[^>]*title=\"(tpss_bcycl_od_statnhm_([0-9]{8})\\.zip)\"[^>]*downloadFile\\('([0-9]+)'\\)");
        var matcher=pattern.matcher(html);var found=new LinkedHashMap<String,RemoteFile>();
        while(matcher.find())found.put(matcher.group(1),new RemoteFile(matcher.group(1),LocalDate.parse(matcher.group(2),DateTimeFormatter.BASIC_ISO_DATE),matcher.group(3)));
        return found.values().stream().sorted(Comparator.comparing(RemoteFile::date).reversed()).toList();
    }
    public synchronized int download()throws Exception {
        Path directory=Path.of(flow.inputDirectory());Files.createDirectories(directory);
        var page=client.send(HttpRequest.newBuilder(LIST).timeout(Duration.ofSeconds(45)).GET().build(),HttpResponse.BodyHandlers.ofString());
        if(page.statusCode()!=200)throw new IllegalStateException("OD_CATALOG_UNAVAILABLE");
        var files=discover(page.body());if(files.isEmpty())throw new IllegalStateException("OD_CATALOG_FORMAT_CHANGED");
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Seoul"));int downloaded=0;
        for(var file:files){
            if(file.date().isAfter(today)||file.date().isBefore(today.minusDays(properties.downloadLookbackDays())))continue;
            Path csv=directory.resolve(file.name().replace(".zip",".csv"));
            // Refresh the newest published file to detect provider corrections by its new checksum.
            if(Files.exists(csv)&&!file.equals(files.getFirst()))continue;
            if(downloaded>=properties.downloadMaxFiles())break;
            Path archive=directory.resolve(file.name()),temp=Files.createTempFile(directory,"od-download-",".part");
            try {
                String form="infId=OA-21229&infSeq=1&seqNo=&seq="+file.sequence();
                var response=client.send(HttpRequest.newBuilder(DOWNLOAD).timeout(Duration.ofMinutes(3))
                    .header("Content-Type","application/x-www-form-urlencoded").header("Referer",LIST.toString())
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofInputStream());
                try(var body=response.body()){
                    if(response.statusCode()!=200 || !response.uri().getHost().endsWith(".seoul.go.kr"))throw new IllegalStateException("OD_DOWNLOAD_RESPONSE");
                    DataFiles.copyLimited(body,temp,properties.maxDownloadBytes());
                }
                try(var input=Files.newInputStream(temp)){if(input.read()!='P'||input.read()!='K')throw new IllegalStateException("OD_NOT_ZIP");}
                DataFiles.extractOdZip(temp,directory,properties.maxExpandedBytes());
                if(!Files.exists(csv))throw new IllegalStateException("OD_EXPECTED_DATE_MISSING");
                Files.move(temp,archive,StandardCopyOption.REPLACE_EXISTING);downloaded++;
            }finally{Files.deleteIfExists(temp);}
        }
        return downloaded;
    }
    public record RemoteFile(String name,LocalDate date,String sequence){}
}
