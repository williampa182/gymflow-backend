package com.gymflow.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class GymflowBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GymflowBackendApplication.class, args);
	}

}
