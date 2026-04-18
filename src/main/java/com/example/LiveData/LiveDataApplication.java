package com.example.LiveData;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LiveDataApplication {
	public static void main(String[] args) {
		SpringApplication.run(LiveDataApplication.class, args);
	}
}
