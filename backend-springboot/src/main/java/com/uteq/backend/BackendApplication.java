package com.uteq.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	/**
     * Handles main.
     *
     * @param args string[] supplied by the caller for this invocation
     */

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}