package com.rightcapital.notification.admin.application.configuration;

public class ConfigurationNotFoundException extends RuntimeException {

    public ConfigurationNotFoundException(String resource, long id) {
        super(resource + " with id " + id + " was not found");
    }
}
