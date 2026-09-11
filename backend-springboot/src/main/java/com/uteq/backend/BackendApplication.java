package com.uteq.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	/**

	 * Executes the main operation.

	 * @param args value required by the operation

	 * @return operation result

	 */

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}