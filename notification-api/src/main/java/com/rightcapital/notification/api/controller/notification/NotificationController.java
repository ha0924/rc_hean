package com.rightcapital.notification.api.controller.notification;

import com.rightcapital.notification.api.application.notification.NotificationAcceptance;
import com.rightcapital.notification.api.application.notification.NotificationSubmissionCommand;
import com.rightcapital.notification.api.application.notification.NotificationSubmissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationSubmissionService submissionService;

    public NotificationController(NotificationSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public NotificationAcceptanceResponse submit(
            @RequestHeader("X-Access-Key") @NotBlank @Size(max = 128) String accessKey,
            @RequestHeader("X-Access-Secret") @NotBlank @Size(max = 72) String secret,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @Valid @RequestBody NotificationRequest request
    ) {
        NotificationAcceptance acceptance = submissionService.submit(new NotificationSubmissionCommand(
                accessKey,
                secret,
                request.eventCode(),
                idempotencyKey,
                request.requestId(),
                request.payload(),
                traceId
        ));
        return NotificationAcceptanceResponse.from(acceptance);
    }
}
