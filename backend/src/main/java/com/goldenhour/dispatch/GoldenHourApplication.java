package com.goldenhour.dispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * GoldenHour: real-time ambulance dispatch for Mumbai.
 * See README.md for the architecture and design.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class GoldenHourApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoldenHourApplication.class, args);
    }
}
