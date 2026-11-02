package com.rightcapital.notification.worker.application;

import com.rightcapital.notification.worker.domain.HttpDeliveryResult;
import org.springframework.stereotype.Component;

/** Initial HTTP outcome policy: 2xx succeeds; timeout, 408, 429, and 5xx retry. */
@Component
class DeliveryResultClassifier {

    boolean isSuccessful(HttpDeliveryResult result) {
        return result.httpStatus() != null && result.httpStatus() >= 200 && result.httpStatus() < 300;
    }

    boolean isRetryable(HttpDeliveryResult result) {
        if (result.httpStatus() == null) {
            return true;
        }
        int status = result.httpStatus();
        return status == 408 || status == 429 || status >= 500;
    }
}
