package com.zerosum.zerosum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ZerosumApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZerosumApplication.class, args);
	}

}
