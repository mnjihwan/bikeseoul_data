package com.ddareungi.data;

import com.ddareungi.data.station.infrastructure.SeoulBikeApiClient;
import com.ddareungi.data.station.infrastructure.SeoulBikeMasterApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "seoul-bike.api.key=local-context-test", "collector.collect-on-startup=false",
        "collector.master.cron=-", "collector.schedule.cron=-", "collector.extended.enabled=false", "collector.extended.cron=-", "spring.flyway.enabled=false"
})
class CollectorContextTest {
    @Autowired SeoulBikeApiClient realtime;
    @Autowired SeoulBikeMasterApiClient master;
    @Test void createsConfiguredApiClientsWithoutMakingExternalCalls() {
        assertThat(realtime).isNotNull();
        assertThat(master).isNotNull();
    }
}
