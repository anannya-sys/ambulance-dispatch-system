package com.goldenhour.dispatch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("GoldenHour ambulance dispatch API")
                .version("1.0.0")
                .description("Real-time ambulance dispatch for Mumbai (simulated fleet): intake, dispatch, "
                        + "hospital matching, live tracking, analytics and the baseline-vs-GoldenHour evaluation."));
    }
}
