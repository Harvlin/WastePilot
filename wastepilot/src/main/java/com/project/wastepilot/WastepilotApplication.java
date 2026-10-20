package com.project.wastepilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WastepilotApplication {

	public static void main(String[] args) {
		SpringApplication.run(WastepilotApplication.class, args);
	}

}
