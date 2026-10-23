package com.example.property_notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PropertyNotificationsApplication {

	public static void main(String[] args) {
		SpringApplication.run(PropertyNotificationsApplication.class, args);
	}

}
