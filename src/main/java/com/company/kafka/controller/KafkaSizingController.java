package com.company.kafka.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Web interface for Kafka Property Sizing Calculator
 * 
 * Provides a user-friendly web form to calculate optimal Kafka properties.
 * Enable with: kafka.sizing.web.enabled=true
 */
@Controller
@ConditionalOnProperty(value = "kafka.sizing.web.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class KafkaSizingController {

    @GetMapping("/kafka-sizing")
    public String sizingForm(Model model) {
        model.addAttribute("messagesPerSecond", 1000);
        model.addAttribute("messageSize", 1024);
        model.addAttribute("processingTime", 10);
        model.addAttribute("consumerCount", 3);
        model.addAttribute("lowLatency", false);
        model.addAttribute("highDurability", true);
        return "kafka-sizing";
    }

    @PostMapping("/kafka-sizing/calculate")
    public String calculateSizing(
            @RequestParam int messagesPerSecond,
            @RequestParam int messageSize,
            @RequestParam int processingTime,
            @RequestParam int consumerCount,
            @RequestParam(defaultValue = "false") boolean lowLatency,
            @RequestParam(defaultValue = "true") boolean highDurability,
            Model model) {

        log.info("Calculating Kafka sizing for: {}msg/s, {}bytes, {}ms processing", 
                messagesPerSecond, messageSize, processingTime);

        KafkaProperties recommendations = calculateRecommendations(
                messagesPerSecond, messageSize, processingTime, 
                consumerCount, lowLatency, highDurability);

        model.addAttribute("recommendations", recommendations);
        model.addAttribute("messagesPerSecond", messagesPerSecond);
        model.addAttribute("messageSize", messageSize);
        model.addAttribute("processingTime", processingTime);
        model.addAttribute("consumerCount", consumerCount);
        model.addAttribute("lowLatency", lowLatency);
        model.addAttribute("highDurability", highDurability);

        return "kafka-sizing-results";
    }

    private KafkaProperties calculateRecommendations(int messagesPerSecond, int messageSize,
                                                   int processingTime, int consumerCount,
                                                   boolean lowLatency, boolean highDurability) {
        
        // Producer calculations
        ProducerProperties producer = calculateProducerProperties(
                messagesPerSecond, messageSize, lowLatency, highDurability);
        
        // Consumer calculations
        ConsumerProperties consumer = calculateConsumerProperties(
                messagesPerSecond, processingTime, lowLatency);
        
        // Topic calculations
        TopicProperties topic = calculateTopicProperties(
                messagesPerSecond, consumerCount, highDurability);

        return new KafkaProperties(producer, consumer, topic);
    }

    private ProducerProperties calculateProducerProperties(int messagesPerSecond, int messageSize,
                                                         boolean lowLatency, boolean highDurability) {
        int batchSize;
        int lingerMs;
        long bufferMemory;
        String acks;
        String compressionType;
        
        if (lowLatency) {
            batchSize = Math.max(1024, messageSize * 2);
            lingerMs = 0;
            bufferMemory = 32 * 1024 * 1024; // 32MB
            acks = "1";
            compressionType = "none";
        } else {
            int messagesPerBatch = Math.min(100, Math.max(10, 1000 / messagesPerSecond));
            batchSize = Math.max(16384, messageSize * messagesPerBatch);
            lingerMs = highDurability ? 20 : 100;
            
            long bytesPerSecond = (long) messagesPerSecond * messageSize;
            bufferMemory = Math.max(64 * 1024 * 1024, bytesPerSecond * 2);
            
            acks = highDurability ? "all" : "1";
            compressionType = "snappy";
        }

        return new ProducerProperties(batchSize, lingerMs, bufferMemory, acks, compressionType);
    }

    private ConsumerProperties calculateConsumerProperties(int messagesPerSecond, int processingTime,
                                                         boolean lowLatency) {
        int maxPollRecords;
        int maxPollIntervalMs;
        int sessionTimeoutMs;
        int fetchMinBytes;
        int concurrency;
        
        if (lowLatency) {
            maxPollRecords = Math.min(50, Math.max(1, 1000 / messagesPerSecond));
            maxPollIntervalMs = 2 * 60 * 1000;
            sessionTimeoutMs = 10 * 1000;
            fetchMinBytes = 1;
            concurrency = 1;
        } else {
            int processingBudgetMs = 30 * 1000;
            maxPollRecords = Math.min(5000, Math.max(100, processingBudgetMs / processingTime));
            maxPollIntervalMs = Math.max(5 * 60 * 1000, maxPollRecords * processingTime * 2);
            sessionTimeoutMs = 30 * 1000;
            fetchMinBytes = Math.max(1024, Math.min(1024 * 1024, messagesPerSecond * 50));
            concurrency = Math.max(1, Math.min(12, messagesPerSecond / 1000));
        }

        return new ConsumerProperties(maxPollRecords, maxPollIntervalMs, 
                                    sessionTimeoutMs, fetchMinBytes, concurrency);
    }

    private TopicProperties calculateTopicProperties(int messagesPerSecond, int consumerCount,
                                                   boolean highDurability) {
        int throughputBasedPartitions = Math.max(1, messagesPerSecond / 10000);
        int consumerBasedPartitions = consumerCount;
        int partitions = Math.max(throughputBasedPartitions, consumerBasedPartitions);
        partitions = Math.min(partitions, 48);
        
        int replicationFactor = highDurability ? 3 : 2;
        
        return new TopicProperties(partitions, replicationFactor);
    }

    public record KafkaProperties(
            ProducerProperties producer,
            ConsumerProperties consumer,
            TopicProperties topic
    ) {}

    public record ProducerProperties(
            int batchSize,
            int lingerMs,
            long bufferMemory,
            String acks,
            String compressionType
    ) {}

    public record ConsumerProperties(
            int maxPollRecords,
            int maxPollIntervalMs,
            int sessionTimeoutMs,
            int fetchMinBytes,
            int concurrency
    ) {}

    public record TopicProperties(
            int partitions,
            int replicationFactor
    ) {}
}
