package com.railway.notificationservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.queue.name}")
    private String queueName;

    @Value("${rabbitmq.exchange.name}")
    private String exchangeName;

    @Value("${rabbitmq.routing.key}")
    private String routingKey;

    // 1. Define the Queue
    @Bean
    public Queue queue() {
        return new Queue(queueName, true); // true = durable (survives broker restart)
    }

    // 2. Define the Exchange
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(exchangeName);
    }

    // 3. Bind Queue to Exchange using Routing Key
    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }

    // 4. Message Converter (Changes Java Objects to JSON for RabbitMQ)
    @Bean
    public MessageConverter converter() {
        return new JacksonJsonMessageConverter();
    }

    // 5. RabbitTemplate Configuration
    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter());
        return rabbitTemplate;
    }
}