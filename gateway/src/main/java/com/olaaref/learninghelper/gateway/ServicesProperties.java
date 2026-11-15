package com.olaaref.learninghelper.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where each microservice listens (see application.yml). The same values feed the gateway routes. */
@ConfigurationProperties("services")
public record ServicesProperties(Service notion, Service dbLab) {
    public record Service(String url) {}
}
