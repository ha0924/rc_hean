package com.rightcapital.notification.admin.application.configuration;

import com.rightcapital.notification.admin.domain.configuration.BusinessApplication;
import com.rightcapital.notification.admin.domain.configuration.BusinessApplicationRepository;
import com.rightcapital.notification.admin.domain.configuration.EventDefinition;
import com.rightcapital.notification.admin.domain.configuration.EventDefinitionRepository;
import com.rightcapital.notification.admin.domain.configuration.ExternalEndpoint;
import com.rightcapital.notification.admin.domain.configuration.ExternalEndpointRepository;
import com.rightcapital.notification.admin.domain.configuration.NotificationRoute;
import com.rightcapital.notification.admin.domain.configuration.NotificationRouteRepository;
import com.rightcapital.notification.admin.security.EndpointHostPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ConfigurationManagementService {

    private final BusinessApplicationRepository businessApplicationRepository;
    private final EventDefinitionRepository eventDefinitionRepository;
    private final ExternalEndpointRepository externalEndpointRepository;
    private final NotificationRouteRepository notificationRouteRepository;
    private final SecretHasher secretHasher;
    private final EventSchemaValidator eventSchemaValidator;
    private final DeliveryConfigurationValidator deliveryConfigurationValidator;
    private final EndpointHostPolicy endpointHostPolicy;

    public ConfigurationManagementService(
            BusinessApplicationRepository businessApplicationRepository,
            EventDefinitionRepository eventDefinitionRepository,
            ExternalEndpointRepository externalEndpointRepository,
            NotificationRouteRepository notificationRouteRepository,
            SecretHasher secretHasher,
            EventSchemaValidator eventSchemaValidator,
            DeliveryConfigurationValidator deliveryConfigurationValidator,
            EndpointHostPolicy endpointHostPolicy
    ) {
        this.businessApplicationRepository = businessApplicationRepository;
        this.eventDefinitionRepository = eventDefinitionRepository;
        this.externalEndpointRepository = externalEndpointRepository;
        this.notificationRouteRepository = notificationRouteRepository;
        this.secretHasher = secretHasher;
        this.eventSchemaValidator = eventSchemaValidator;
        this.deliveryConfigurationValidator = deliveryConfigurationValidator;
        this.endpointHostPolicy = endpointHostPolicy;
    }

    public BusinessApplication createBusinessApplication(BusinessApplicationCommand command) {
        return businessApplicationRepository.insert(toBusinessApplication(null, command));
    }

    public BusinessApplication updateBusinessApplication(long id, BusinessApplicationCommand command) {
        getBusinessApplication(id);
        return businessApplicationRepository.update(toBusinessApplication(id, command));
    }

    @Transactional(readOnly = true)
    public BusinessApplication getBusinessApplication(long id) {
        return businessApplicationRepository.findById(id)
                .orElseThrow(() -> new ConfigurationNotFoundException("business application", id));
    }

    @Transactional(readOnly = true)
    public List<BusinessApplication> listBusinessApplications() {
        return businessApplicationRepository.findAll();
    }

    public void deleteBusinessApplication(long id) {
        if (!businessApplicationRepository.deleteById(id)) {
            throw new ConfigurationNotFoundException("business application", id);
        }
    }

    public EventDefinition createEventDefinition(EventDefinition eventDefinition) {
        requireBusinessApplication(eventDefinition.applicationId());
        eventSchemaValidator.validate(eventDefinition.payloadSchema());
        return eventDefinitionRepository.insert(withoutId(eventDefinition));
    }

    public EventDefinition updateEventDefinition(long id, EventDefinition eventDefinition) {
        getEventDefinition(id);
        requireBusinessApplication(eventDefinition.applicationId());
        eventSchemaValidator.validate(eventDefinition.payloadSchema());
        return eventDefinitionRepository.update(withId(id, eventDefinition));
    }

    @Transactional(readOnly = true)
    public EventDefinition getEventDefinition(long id) {
        return eventDefinitionRepository.findById(id)
                .orElseThrow(() -> new ConfigurationNotFoundException("event definition", id));
    }

    @Transactional(readOnly = true)
    public List<EventDefinition> listEventDefinitions() {
        return eventDefinitionRepository.findAll();
    }

    public void deleteEventDefinition(long id) {
        if (!eventDefinitionRepository.deleteById(id)) {
            throw new ConfigurationNotFoundException("event definition", id);
        }
    }

    public ExternalEndpoint createExternalEndpoint(ExternalEndpoint endpoint) {
        validateEndpoint(endpoint);
        return externalEndpointRepository.insert(withoutId(endpoint));
    }

    public ExternalEndpoint updateExternalEndpoint(long id, ExternalEndpoint endpoint) {
        getExternalEndpoint(id);
        validateEndpoint(endpoint);
        return externalEndpointRepository.update(withId(id, endpoint));
    }

    @Transactional(readOnly = true)
    public ExternalEndpoint getExternalEndpoint(long id) {
        return externalEndpointRepository.findById(id)
                .orElseThrow(() -> new ConfigurationNotFoundException("external endpoint", id));
    }

    @Transactional(readOnly = true)
    public List<ExternalEndpoint> listExternalEndpoints() {
        return externalEndpointRepository.findAll();
    }

    public void deleteExternalEndpoint(long id) {
        if (!externalEndpointRepository.deleteById(id)) {
            throw new ConfigurationNotFoundException("external endpoint", id);
        }
    }

    public NotificationRoute createNotificationRoute(NotificationRoute route) {
        requireEventDefinition(route.eventDefinitionId());
        deliveryConfigurationValidator.validate(route, getExternalEndpoint(route.externalEndpointId()));
        return notificationRouteRepository.insert(withoutId(route));
    }

    public NotificationRoute updateNotificationRoute(long id, NotificationRoute route) {
        getNotificationRoute(id);
        requireEventDefinition(route.eventDefinitionId());
        deliveryConfigurationValidator.validate(route, getExternalEndpoint(route.externalEndpointId()));
        return notificationRouteRepository.update(withId(id, route));
    }

    @Transactional(readOnly = true)
    public NotificationRoute getNotificationRoute(long id) {
        return notificationRouteRepository.findById(id)
                .orElseThrow(() -> new ConfigurationNotFoundException("notification route", id));
    }

    @Transactional(readOnly = true)
    public List<NotificationRoute> listNotificationRoutes() {
        return notificationRouteRepository.findAll();
    }

    public void deleteNotificationRoute(long id) {
        if (!notificationRouteRepository.deleteById(id)) {
            throw new ConfigurationNotFoundException("notification route", id);
        }
    }

    private BusinessApplication toBusinessApplication(Long id, BusinessApplicationCommand command) {
        return new BusinessApplication(
                id,
                command.code(),
                command.name(),
                command.accessKey(),
                secretHasher.hash(command.secret()),
                command.status(),
                null,
                null
        );
    }

    private void validateEndpoint(ExternalEndpoint endpoint) {
        endpointHostPolicy.validate(endpoint.urlTemplate());
        deliveryConfigurationValidator.validate(endpoint);
    }

    private void requireBusinessApplication(long id) {
        if (!businessApplicationRepository.existsById(id)) {
            throw new ConfigurationNotFoundException("business application", id);
        }
    }

    private void requireEventDefinition(long id) {
        if (!eventDefinitionRepository.existsById(id)) {
            throw new ConfigurationNotFoundException("event definition", id);
        }
    }

    private EventDefinition withoutId(EventDefinition eventDefinition) {
        return new EventDefinition(
                null,
                eventDefinition.applicationId(),
                eventDefinition.eventCode(),
                eventDefinition.name(),
                eventDefinition.payloadSchema(),
                eventDefinition.status(),
                null,
                null
        );
    }

    private EventDefinition withId(long id, EventDefinition eventDefinition) {
        return new EventDefinition(
                id,
                eventDefinition.applicationId(),
                eventDefinition.eventCode(),
                eventDefinition.name(),
                eventDefinition.payloadSchema(),
                eventDefinition.status(),
                null,
                null
        );
    }

    private ExternalEndpoint withoutId(ExternalEndpoint endpoint) {
        return new ExternalEndpoint(
                null,
                endpoint.code(),
                endpoint.name(),
                endpoint.httpMethod(),
                endpoint.urlTemplate(),
                endpoint.authType(),
                endpoint.authConfig(),
                endpoint.defaultHeaders(),
                endpoint.timeoutMs(),
                endpoint.maxConcurrency(),
                endpoint.successRule(),
                endpoint.retryableRule(),
                endpoint.status(),
                null,
                null
        );
    }

    private ExternalEndpoint withId(long id, ExternalEndpoint endpoint) {
        return new ExternalEndpoint(
                id,
                endpoint.code(),
                endpoint.name(),
                endpoint.httpMethod(),
                endpoint.urlTemplate(),
                endpoint.authType(),
                endpoint.authConfig(),
                endpoint.defaultHeaders(),
                endpoint.timeoutMs(),
                endpoint.maxConcurrency(),
                endpoint.successRule(),
                endpoint.retryableRule(),
                endpoint.status(),
                null,
                null
        );
    }

    private NotificationRoute withoutId(NotificationRoute route) {
        return new NotificationRoute(
                null,
                route.eventDefinitionId(),
                route.externalEndpointId(),
                route.requestMapping(),
                route.status(),
                null,
                null
        );
    }

    private NotificationRoute withId(long id, NotificationRoute route) {
        return new NotificationRoute(
                id,
                route.eventDefinitionId(),
                route.externalEndpointId(),
                route.requestMapping(),
                route.status(),
                null,
                null
        );
    }
}
