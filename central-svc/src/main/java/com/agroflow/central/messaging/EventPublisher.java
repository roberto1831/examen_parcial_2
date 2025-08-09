package com.agroflow.central.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class EventPublisher {

    private final AmqpTemplate amqpTemplate;
    private final DirectExchange exchange;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${agroflow.rabbit.routingKey.nueva}")
    private String routingNueva;

    public EventPublisher(AmqpTemplate amqpTemplate, DirectExchange exchange) {
        this.amqpTemplate = amqpTemplate;
        this.exchange = exchange;
    }

    public void publishNuevaCosecha(Map<String, Object> payload) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event_type", "nueva_cosecha");
            event.put("timestamp", OffsetDateTime.now().toString());
            event.put("payload", payload);
            String json = mapper.writeValueAsString(event);
            amqpTemplate.convertAndSend(exchange.getName(), routingNueva, json);
        } catch (Exception e) {
            throw new RuntimeException("Error publicando evento a RabbitMQ", e);
        }
    }
}
