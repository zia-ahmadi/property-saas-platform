package com.example.property_core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PropertyCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(PropertyCoreApplication.class, args);
	}

}
