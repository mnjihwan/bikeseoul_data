package com.ddareungi.data;

import com.ddareungi.data.config.SeoulBikeApiProperties;
import com.ddareungi.data.config.FlowCsvProperties;
import com.ddareungi.data.extended.ExtendedProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({SeoulBikeApiProperties.class, FlowCsvProperties.class, ExtendedProperties.class})
@SpringBootApplication
public class BikeSeoulDataApplication {
    public static void main(String[] args) {
        SpringApplication.run(BikeSeoulDataApplication.class, args);
    }
}
