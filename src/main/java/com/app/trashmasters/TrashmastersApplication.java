package com.app.trashmasters;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableScheduling
public class TrashmastersApplication {

	static {
		// Load .env file only if it exists — falls back to IntelliJ/system env vars
		java.io.File envFile = new java.io.File(System.getProperty("user.dir"), ".env");
		if (envFile.exists()) {
			Dotenv dotenv = Dotenv.configure()
					.directory(System.getProperty("user.dir"))
					.load();
			dotenv.entries().forEach(entry ->
					System.setProperty(entry.getKey(), entry.getValue())
			);
			System.out.println("✅ Loaded environment from .env file");
		} else {
			System.out.println("ℹ️ No .env file found — using system/IntelliJ environment variables");
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(TrashmastersApplication.class, args);
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
