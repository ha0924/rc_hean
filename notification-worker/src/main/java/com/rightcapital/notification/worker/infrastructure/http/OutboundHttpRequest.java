package com.rightcapital.notification.worker.infrastructure.http;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.net.URI;

record OutboundHttpRequest(
        HttpMethod method,
        URI uri,
        HttpHeaders headers,
        MediaType contentType,
        String body
) {
}
