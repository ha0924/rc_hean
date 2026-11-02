package com.rightcapital.notification.worker.domain;

/** Result data that is safe to persist on {@code delivery_task}. */
public record HttpDeliveryResult(
        Integer httpStatus,
        long durationMs,
        String errorType,
        String errorMessage,
        String responseExcerpt
) {

    public static HttpDeliveryResult transportFailure(long durationMs, String errorType, String errorMessage) {
        return new HttpDeliveryResult(null, durationMs, errorType, errorMessage, null);
    }

    public static HttpDeliveryResult response(int httpStatus, long durationMs, String responseExcerpt) {
        String errorType = httpStatus >= 400 ? "HTTP_STATUS" : null;
        String errorMessage = httpStatus >= 400 ? "External endpoint returned HTTP " + httpStatus : null;
        return new HttpDeliveryResult(httpStatus, durationMs, errorType, errorMessage, responseExcerpt);
    }
}
