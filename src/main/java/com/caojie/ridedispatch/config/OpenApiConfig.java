package com.caojie.ridedispatch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rideDispatchOpenApi() {
        return new OpenAPI().info(new Info()
                .title("网约车派单系统 API")
                .description("司机上线、下单派单、接单、行程推进、取消订单")
                .version("v1"));
    }
}