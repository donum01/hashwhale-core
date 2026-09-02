package com.hashwhale.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Configuration for the deliberately opt-in interview showcase data reset. */
@Getter
@Setter
@Component
@Profile("demo")
@ConfigurationProperties(prefix = "app.demo-seed")
public class DemoDataProperties {

    private boolean enabled;
    private boolean reset;
    private String email = "demo@hashwhale.com";
    private String password;
}
