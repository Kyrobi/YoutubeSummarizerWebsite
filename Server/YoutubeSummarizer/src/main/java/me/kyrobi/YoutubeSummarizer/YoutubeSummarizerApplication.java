package me.kyrobi.YoutubeSummarizer;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication
public class YoutubeSummarizerApplication {

	@Value("${server.port}")
	private int port;

	public static void main(String[] args) {
		SpringApplication.run(YoutubeSummarizerApplication.class, args);
	}

	@PostConstruct
	public void logPort() {
		System.out.println("Server running on port " + port);
	}

}
