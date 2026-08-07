package com.limitedgoods.limitedgoods.common.config.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderLifecycleTopic(
            @Value("${app.kafka.topics.order-lifecycle.name}")
            String topicName,
            @Value("${app.kafka.topics.order-lifecycle.partitions}")
            int partitions,
            @Value("${app.kafka.topics.order-lifecycle.replicas}")
            short replicas
    ) {
        return TopicBuilder
                .name(topicName)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}