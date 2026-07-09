package com.paymentprocessor.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String PAYMENTS_EXCHANGE = "payments";
    public static final String PAYMENT_PROCESS_QUEUE = "payment.process";
    public static final String PAYMENT_OK_QUEUE = "payment.ok";
    public static final String PAYMENT_DLQ = "payment.dlq";
    public static final String DLX_EXCHANGE = "payments.dlx";

    public static final String ROUTING_KEY_CREATED = "payment.created";
    public static final String ROUTING_KEY_OK = "payment.ok";
    public static final String ROUTING_KEY_DLQ = "payment.dlq";

    // exchange principal
    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENTS_EXCHANGE);
    }

    // ---- exchange de dead letter ----
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    // ---- fila principal de processamento, já apontando pra DLQ em caso de falha ----
    @Bean
    public Queue paymentProcessQueue() {
        return QueueBuilder.durable(PAYMENT_PROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DLQ)
                .build();
    }

    // ---- fila de sucesso (quem quiser saber que um pagamento foi concluído escuta aqui) ----
    @Bean
    public Queue paymentOkQueue() {
        return QueueBuilder.durable(PAYMENT_OK_QUEUE).build();
    }

    // ---- dead letter queue ----
    @Bean
    public Queue paymentDlqQueue() {
        return QueueBuilder.durable(PAYMENT_DLQ).build();
    }

    // ---- bindings: liga fila + exchange + routing key ----
    @Bean
    public Binding paymentProcessBinding() {
        return BindingBuilder.bind(paymentProcessQueue())
                .to(paymentExchange())
                .with(ROUTING_KEY_CREATED);
    }

    @Bean
    public Binding paymentOkBinding() {
        return BindingBuilder.bind(paymentOkQueue())
                .to(paymentExchange())
                .with(ROUTING_KEY_OK);
    }

    @Bean
    public Binding paymentDlqBinding() {
        return BindingBuilder.bind(paymentDlqQueue())
                .to(dlxExchange())
                .with(ROUTING_KEY_DLQ);
    }

    // ---- serialização JSON das mensagens (em vez do padrão Java Serializable) ----
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

}
