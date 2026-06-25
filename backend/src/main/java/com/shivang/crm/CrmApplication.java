package com.shivang.crm;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CrmApplication {

	public static void main(String[] args) {
		  // DEBUG: Print current timezone
        System.out.println("Current default timezone: " + java.util.TimeZone.getDefault().getID());
        System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());


		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
		SpringApplication.run(CrmApplication.class, args);
	}
 
}
