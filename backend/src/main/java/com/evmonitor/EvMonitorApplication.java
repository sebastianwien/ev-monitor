package com.evmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class EvMonitorApplication {

	public static void main(String[] args) {
		SpringApplication.run(EvMonitorApplication.class, args);
	}

}
