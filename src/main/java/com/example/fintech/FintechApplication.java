package com.example.fintech;

import com.example.fintech.config.DatabaseInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class FintechApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(FintechApplication.class);
		application.addListeners(new DatabaseInitializer());
		application.run(args);
	}

}
