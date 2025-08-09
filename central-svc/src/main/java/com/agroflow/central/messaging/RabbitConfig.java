package com.agroflow.central.messaging;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RabbitConfig {

    @Value("${agroflow.rabbit.exchange}")
    private String exchangeName;

    @Bean
    public DirectExchange cosechasExchange() {
        return new DirectExchange(exchangeName, true, false);
    }
}
