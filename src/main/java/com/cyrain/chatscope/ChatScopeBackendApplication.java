package com.cyrain.chatscope;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChatScopeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatScopeBackendApplication.class, args);
    }
}
