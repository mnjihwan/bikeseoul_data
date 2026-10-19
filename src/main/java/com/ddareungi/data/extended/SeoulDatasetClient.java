package com.ddareungi.data.extended;

import com.ddareungi.data.config.SeoulBikeApiProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.List;

@Component
public class SeoulDatasetClient {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(SeoulDatasetClient.class);
    private final RestClient client;
    private final JsonMapper mapper;
    private final SeoulBikeApiProperties properties;
    public SeoulDatasetClient(SeoulBikeApiProperties properties, RestClient.Builder builder, JsonMapper mapper) {
        this.properties=properties; this.client=builder.baseUrl(properties.baseUrl()).build(); this.mapper=mapper;
    }
    public List<JsonNode> fetch(String service, String envelope, String suffix) {
        var result=new ArrayList<JsonNode>();
        long expected=-1;
        try {
            for (int page=0;page<200;page++) {
                int start=page*1000+1, end=start+999;
                String body=client.get().uri("/{key}/json/{service}/{start}/{end}"+suffix,
                    properties.key(),service,start,end).retrieve().body(String.class);
                var parsed=parse(body,envelope);
                if (parsed.empty()) {
                    if (expected>result.size()) throw new IllegalStateException("INCOMPLETE_PAGE");
                    return result;
                }
                if (expected<0) expected=parsed.total();
                result.addAll(parsed.rows());
                if(page%10==0)log.info("{} {}: {}/{}행 조회",service,suffix,result.size(),expected);
                if (result.size()>=expected) return result;
            }
            throw new IllegalStateException("PAGE_LIMIT");
        } catch (Exception exception) {
            // RestClient exception messages contain a URL with the API key. Never propagate it.
            throw new IllegalStateException("서울 데이터 호출 실패: "+service);
        }
    }
    public Page parse(String body,String name) throws Exception {
        var root=mapper.readTree(body); var envelope=root.path(name);
        if (envelope.isMissingNode() || envelope.isNull()) envelope=root;
        String code=envelope.path("RESULT").path("CODE").asString("");
        if ("INFO-200".equals(code)) return new Page(0,List.of(),true);
        if (!"INFO-000".equals(code) || !envelope.path("row").isArray()) throw new IllegalStateException("PROVIDER_RESPONSE");
        long total=Long.parseLong(envelope.path("list_total_count").asString("0"));
        var rows=new ArrayList<JsonNode>(); for(var row:envelope.path("row"))rows.add(row);
        if (total<rows.size()) throw new IllegalStateException("INVALID_TOTAL");
        return new Page(total,rows,false);
    }
    public record Page(long total,List<JsonNode> rows,boolean empty) {}
}
