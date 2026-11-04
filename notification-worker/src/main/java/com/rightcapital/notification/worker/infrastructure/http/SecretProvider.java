package com.rightcapital.notification.worker.infrastructure.http;

interface SecretProvider {

    String resolve(String secretRef);
}
