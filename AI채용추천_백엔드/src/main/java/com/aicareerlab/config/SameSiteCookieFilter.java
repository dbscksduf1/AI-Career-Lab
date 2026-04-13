package com.aicareerlab.config;

import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.apache.tomcat.util.http.SameSiteCookies;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SameSiteCookieFilter {

    @Bean
    public TomcatContextCustomizer sameSiteCookiesConfig() {
        return context -> {
            Rfc6265CookieProcessor processor = new Rfc6265CookieProcessor();
            processor.setSameSiteCookies(SameSiteCookies.NONE.getValue());
            context.setCookieProcessor(processor);
        };
    }
}
