package com.rightcapital.notification.admin.controller.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcapital.notification.admin.application.configuration.BusinessApplicationCommand;
import com.rightcapital.notification.admin.application.configuration.ConfigurationManagementService;
import com.rightcapital.notification.admin.domain.configuration.EventDefinition;
import com.rightcapital.notification.admin.domain.configuration.ExternalEndpoint;
import com.rightcapital.notification.admin.domain.configuration.NotificationRoute;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Validated
public class ConfigurationController {

    private final ConfigurationManagementService service;
    private final ObjectMapper objectMapper;

    public ConfigurationController(ConfigurationManagementService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/business-applications")
    public ResponseEntity<BusinessApplicationResponse> createBusinessApplication(
            @Valid @RequestBody BusinessApplicationRequest request
    ) {
        BusinessApplicationResponse response = BusinessApplicationResponse.from(service.createBusinessApplication(
                new BusinessApplicationCommand(
                        request.code(), request.name(), request.accessKey(), request.secret(), request.status()
                )
        ));
        return created("/business-applications/{id}", response.id(), response);
    }

    @GetMapping("/business-applications")
    public List<BusinessApplicationResponse> listBusinessApplications() {
        return service.listBusinessApplications().stream().map(BusinessApplicationResponse::from).toList();
    }

    @GetMapping("/business-applications/{id}")
    public BusinessApplicationResponse getBusinessApplication(@PathVariable @Positive long id) {
        return BusinessApplicationResponse.from(service.getBusinessApplication(id));
    }

    @PutMapping("/business-applications/{id}")
    public BusinessApplicationResponse updateBusinessApplication(
            @PathVariable @Positive long id,
            @Valid @RequestBody BusinessApplicationRequest request
    ) {
        return BusinessApplicationResponse.from(service.updateBusinessApplication(id,
                new BusinessApplicationCommand(
                        request.code(), request.name(), request.accessKey(), request.secret(), request.status()
                )
        ));
    }

    @DeleteMapping("/business-applications/{id}")
    public ResponseEntity<Void> deleteBusinessApplication(@PathVariable @Positive long id) {
        service.deleteBusinessApplication(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/event-definitions")
    public ResponseEntity<EventDefinitionResponse> createEventDefinition(
            @Valid @RequestBody EventDefinitionRequest request
    ) {
        EventDefinitionResponse response = eventDefinitionResponse(service.createEventDefinition(toEventDefinition(request)));
        return created("/event-definitions/{id}", response.id(), response);
    }

    @GetMapping("/event-definitions")
    public List<EventDefinitionResponse> listEventDefinitions() {
        return service.listEventDefinitions().stream().map(this::eventDefinitionResponse).toList();
    }

    @GetMapping("/event-definitions/{id}")
    public EventDefinitionResponse getEventDefinition(@PathVariable @Positive long id) {
        return eventDefinitionResponse(service.getEventDefinition(id));
    }

    @PutMapping("/event-definitions/{id}")
    public EventDefinitionResponse updateEventDefinition(
            @PathVariable @Positive long id,
            @Valid @RequestBody EventDefinitionRequest request
    ) {
        return eventDefinitionResponse(service.updateEventDefinition(id, toEventDefinition(request)));
    }

    @DeleteMapping("/event-definitions/{id}")
    public ResponseEntity<Void> deleteEventDefinition(@PathVariable @Positive long id) {
        service.deleteEventDefinition(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/external-endpoints")
    public ResponseEntity<ExternalEndpointResponse> createExternalEndpoint(
            @Valid @RequestBody ExternalEndpointRequest request
    ) {
        ExternalEndpointResponse response = externalEndpointResponse(service.createExternalEndpoint(toExternalEndpoint(request)));
        return created("/external-endpoints/{id}", response.id(), response);
    }

    @GetMapping("/external-endpoints")
    public List<ExternalEndpointResponse> listExternalEndpoints() {
        return service.listExternalEndpoints().stream().map(this::externalEndpointResponse).toList();
    }

    @GetMapping("/external-endpoints/{id}")
    public ExternalEndpointResponse getExternalEndpoint(@PathVariable @Positive long id) {
        return externalEndpointResponse(service.getExternalEndpoint(id));
    }

    @PutMapping("/external-endpoints/{id}")
    public ExternalEndpointResponse updateExternalEndpoint(
            @PathVariable @Positive long id,
            @Valid @RequestBody ExternalEndpointRequest request
    ) {
        return externalEndpointResponse(service.updateExternalEndpoint(id, toExternalEndpoint(request)));
    }

    @DeleteMapping("/external-endpoints/{id}")
    public ResponseEntity<Void> deleteExternalEndpoint(@PathVariable @Positive long id) {
        service.deleteExternalEndpoint(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notification-routes")
    public ResponseEntity<NotificationRouteResponse> createNotificationRoute(
            @Valid @RequestBody NotificationRouteRequest request
    ) {
        NotificationRouteResponse response = notificationRouteResponse(service.createNotificationRoute(toNotificationRoute(request)));
        return created("/notification-routes/{id}", response.id(), response);
    }

    @GetMapping("/notification-routes")
    public List<NotificationRouteResponse> listNotificationRoutes() {
        return service.listNotificationRoutes().stream().map(this::notificationRouteResponse).toList();
    }

    @GetMapping("/notification-routes/{id}")
    public NotificationRouteResponse getNotificationRoute(@PathVariable @Positive long id) {
        return notificationRouteResponse(service.getNotificationRoute(id));
    }

    @PutMapping("/notification-routes/{id}")
    public NotificationRouteResponse updateNotificationRoute(
            @PathVariable @Positive long id,
            @Valid @RequestBody NotificationRouteRequest request
    ) {
        return notificationRouteResponse(service.updateNotificationRoute(id, toNotificationRoute(request)));
    }

    @DeleteMapping("/notification-routes/{id}")
    public ResponseEntity<Void> deleteNotificationRoute(@PathVariable @Positive long id) {
        service.deleteNotificationRoute(id);
        return ResponseEntity.noContent().build();
    }

    private EventDefinition toEventDefinition(EventDefinitionRequest request) {
        return new EventDefinition(
                null,
                request.applicationId(),
                request.eventCode(),
                request.name(),
                json(request.payloadSchema()),
                request.status(),
                null,
                null
        );
    }

    private ExternalEndpoint toExternalEndpoint(ExternalEndpointRequest request) {
        return new ExternalEndpoint(
                null,
                request.code(),
                request.name(),
                request.httpMethod(),
                request.urlTemplate(),
                request.authType(),
                jsonOrNull(request.authConfig()),
                request.defaultHeaders() == null ? null : json(request.defaultHeaders()),
                request.timeoutMs(),
                request.maxConcurrency(),
                json(request.successRule()),
                json(request.retryableRule()),
                request.status(),
                null,
                null
        );
    }

    private NotificationRoute toNotificationRoute(NotificationRouteRequest request) {
        return new NotificationRoute(
                null,
                request.eventDefinitionId(),
                request.externalEndpointId(),
                json(request.requestMapping()),
                request.status(),
                null,
                null
        );
    }

    private EventDefinitionResponse eventDefinitionResponse(EventDefinition eventDefinition) {
        return EventDefinitionResponse.from(eventDefinition, readJson(eventDefinition.payloadSchema()));
    }

    private ExternalEndpointResponse externalEndpointResponse(ExternalEndpoint endpoint) {
        return ExternalEndpointResponse.from(
                endpoint,
                readJson(endpoint.defaultHeaders()),
                readJson(endpoint.successRule()),
                readJson(endpoint.retryableRule())
        );
    }

    private NotificationRouteResponse notificationRouteResponse(NotificationRoute route) {
        return NotificationRouteResponse.from(route, readJson(route.requestMapping()));
    }

    private String json(JsonNode node) {
        return node.toString();
    }

    private String jsonOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : json(node);
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored configuration JSON is invalid", exception);
        }
    }

    private <T> ResponseEntity<T> created(String path, long id, T response) {
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/admin" + path)
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(response);
    }
}
