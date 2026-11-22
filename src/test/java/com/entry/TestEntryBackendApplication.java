package com.entry;

import org.springframework.boot.SpringApplication;

public class TestEntryBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(EntryBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
