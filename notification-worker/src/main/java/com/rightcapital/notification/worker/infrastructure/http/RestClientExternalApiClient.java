package com.rightcapital.notification.worker.infrastructure.http;

import com.rightcapital.notification.worker.domain.DeliveryConfigurationException;
import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;
import com.rightcapital.notification.worker.domain.ExternalApiClient;
import com.rightcapital.notification.worker.domain.HttpDeliveryResult;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Executes an already mapped outbound request.
 */
@Component
class RestClientExternalApiClient implements ExternalApiClient {

    private static final int MAX_RESPONSE_EXCERPT_LENGTH = 2048;

    private final OutboundRequestFactory outboundRequestFactory;

    RestClientExternalApiClient(OutboundRequestFactory outboundRequestFactory) {
        this.outboundRequestFactory = outboundRequestFactory;
    }

    @Override
    public HttpDeliveryResult deliver(DeliveryTaskDetail task) {
        long startedAt = System.nanoTime();
        try {
            OutboundHttpRequest outboundRequest = outboundRequestFactory.create(task);
            Duration timeout = Duration.ofMillis(task.timeoutMs());
            return restClient(timeout)
                    .method(outboundRequest.method())
                    .uri(outboundRequest.uri())
                    .headers(requestHeaders -> requestHeaders.putAll(outboundRequest.headers()))
                    .contentType(outboundRequest.contentType())
                    .body(outboundRequest.body())
                    .exchange((request, response) -> HttpDeliveryResult.response(
                            response.getStatusCode().value(),
                            elapsedMillis(startedAt),
                            truncate(StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8))
                    ));
        } catch (DeliveryConfigurationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            return HttpDeliveryResult.transportFailure(
                    elapsedMillis(startedAt), "NETWORK", safeMessage(exception)
            );
        }
    }

    private RestClient restClient(Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String truncate(String responseBody) {
        if (responseBody.length() <= MAX_RESPONSE_EXCERPT_LENGTH) {
            return responseBody;
        }
        return responseBody.substring(0, MAX_RESPONSE_EXCERPT_LENGTH);
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
