package com.caojie.ridedispatch;

import com.caojie.ridedispatch.config.RideDispatchProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@MapperScan("com.caojie.ridedispatch.mapper")
@SpringBootApplication
@EnableConfigurationProperties(RideDispatchProperties.class)
public class RideDispatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(RideDispatchApplication.class, args);
	}

}
