package com.ibbe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;

@SpringBootApplication
@EnableScheduling
public class BootApplication {

  public static void main(String[] args) {
    SpringApplication.run(BootApplication.class, args);
  }
  
  /**
   * Configure Jackson for standard date/time handling
   */
  @Bean
  public Jackson2ObjectMapperBuilderCustomizer jacksonObjectMapperCustomization() {
    return builder -> {
      // Configure basic mapper settings
      builder.findModulesViaServiceLoader(true);
      // Use ISO formats for timestamps (preferred format for API/JSON)
      builder.timeZone(ZoneId.systemDefault().toString());
    };
  }
}
