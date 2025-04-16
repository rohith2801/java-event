package com.demo.java_event.service;

import com.demo.java_event.model.Reaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ReactionService {
    private static final Logger logger = LoggerFactory.getLogger(ReactionService.class);

    private final Map<String, Map<UUID, SseEmitter>> eventEmitters = new ConcurrentHashMap<>();

    private final Map<String, Map<UUID, List<Reaction>>> eventMessageBuffers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${sse.heartbeat.enabled:false}")
    private Boolean isHeartbeatEnabled;

    @Value("${sse.heartbeat.interval:30}")
    private Integer heartbeatInterval;

    @Value("${sse.timeout:60}")
    private Integer sseTimeout;

    @Value("${sse.resend.enabled:true}")
    private Boolean isResendEnabled;

    /**
     * Saves a reaction and sends it to all subscribers of the event.
     */
    public void saveReaction(final String eventId, final Reaction reaction) {
        Map<UUID, SseEmitter> emitters = eventEmitters.get(eventId);
        if (emitters == null) {
            System.out.printf("No emitters found for eventId %s%n", eventId);
            logger.warn("No emitters found for eventId {}", eventId);
            return;
        }

        emitters.forEach((uuid, sseEmitter) -> {
            try {
                bufferMessage(eventId, uuid, reaction);
                System.out.printf("Sending reaction to client %s for event %s%n", uuid, eventId);
                logger.info("Sending reaction to client {} for event {}", uuid, eventId);
                sseEmitter.send(SseEmitter.event().name("reactions").data(objectMapper.writeValueAsString(reaction)));
            } catch (IOException e) {
                System.out.printf("Client %s disconnected abruptly: %s%n", uuid, e);
                logger.warn("Client {} disconnected abruptly:", uuid, e);
                cleanupEmitter(eventId, uuid);
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("ResponseBodyEmitter has already completed")) {
                    System.out.printf("Emitter already completed for client %s: %s%n", uuid, e);
                    logger.warn("Emitter already completed for client {}:", uuid, e);
                    cleanupEmitter(eventId, uuid);
                }
            } catch (Exception e) {
                System.out.printf("Unexpected exception for client %s: %s%n", uuid, e);
                logger.error("Unexpected exception for client {}:", uuid, e);
                cleanupEmitter(eventId, uuid);
            }
        });
    }

    public void unsubscribe(String eventId, UUID sessionId) {
        System.out.printf("Unsubscribing event %s with uuid %s%n", eventId, sessionId);
        logger.info("Unsubscribing event {} with uuid {}", eventId, sessionId);
        Map<UUID, SseEmitter> emitters = eventEmitters.get(eventId);
        if (emitters != null) {
            SseEmitter sseEmitter = emitters.get(sessionId);
            if (sseEmitter != null) {
                emitters.remove(sessionId);
                if (emitters.isEmpty()) {
                    eventEmitters.remove(eventId);
                }

                sseEmitter.complete();
            }
        }
    }

    public SseEmitter subscribe(final String eventId, final UUID uuid) {
        SseEmitter sseEmitter = new SseEmitter(sseTimeout * 1000L);

        eventEmitters.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>()).put(uuid, sseEmitter);
        if (isHeartbeatEnabled) {
            System.out.printf("Heartbeat is enabled. Hence scheduling heartbeat for eventId %s, uuid %s%n", eventId, uuid);
            logger.info("Heartbeat is enabled. Hence scheduling heartbeat for eventId {}, uuid {}", eventId, uuid);
            scheduleHeartbeat(eventId, uuid, sseEmitter);
        }

        sseEmitter.onCompletion(() -> {
            eventMessageBuffers.getOrDefault(eventId, Collections.emptyMap()).remove(uuid);
            System.out.printf("SSE emitter completed for client %s on event %s%n", uuid, eventId);
            logger.info("SSE emitter completed for client {} on event {}", uuid, eventId);
            cleanupEmitter(eventId, uuid);
        });

        sseEmitter.onTimeout(() -> {
            System.out.printf("SSE emitter timed out for client %s on event %s%n", uuid, eventId);
            logger.error("SSE emitter timed out for client {} on event {}", uuid, eventId);
            cleanupEmitter(eventId, uuid);
            sseEmitter.complete();
        });

        sseEmitter.onError((e) -> {
            System.out.printf("SSE emitter error for client %s on event %s: %s%n", uuid, eventId, e.getMessage());
            logger.error("SSE emitter error for client {} on event {}: {}", uuid, eventId, e.getMessage());
            cleanupEmitter(eventId, uuid);
            sseEmitter.completeWithError(e);
        });

        if (isResendEnabled) {
            Executors.newFixedThreadPool(1)
                    .execute(() -> resendBufferedMessages(eventId, uuid, sseEmitter));
        }

        System.out.printf("Client %s subscribed to event %s%n", uuid, eventId);
        logger.info("Client {} subscribed to event {}", uuid, eventId);
        return sseEmitter;
    }

    private void cleanupEmitter(String eventId, UUID uuid) {
        Map<UUID, SseEmitter> emitters = eventEmitters.get(eventId);
        if (emitters != null) {
            emitters.remove(uuid);
            if (emitters.isEmpty()) {
                eventEmitters.remove(eventId);
            }
        }

        Map<UUID, List<Reaction>> messageBuffer = eventMessageBuffers.get(eventId);
        if (messageBuffer != null) {
            messageBuffer.remove(uuid);
            if (messageBuffer.isEmpty()) {
                eventMessageBuffers.remove(eventId);
            }
        }
    }

    private void bufferMessage(String eventId, UUID uuid, Reaction reaction) {
        eventMessageBuffers.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(reaction);
    }

    private void resendBufferedMessages(String eventId, UUID uuid, SseEmitter sseEmitter) {
        Map<UUID, List<Reaction>> messageBuffer = eventMessageBuffers.get(eventId);
        if (messageBuffer != null) {
            List<Reaction> bufferedMessages = messageBuffer.get(uuid);
            if (bufferedMessages != null) {
                bufferedMessages.forEach(reaction -> {
                    try {
                        System.out.printf("Resending buffered message to client %s for event %s%n", uuid, eventId);
                        logger.info("Resending buffered message to client {} for event {}", uuid, eventId);
                        sseEmitter.send(reaction);
                    } catch (IOException e) {
                        System.out.printf("Failed to resend buffered message to client %s: %s%n", uuid, e);
                        logger.error("Failed to resend buffered message to client {}:", uuid, e);
                    }
                });
                bufferedMessages.clear();
            }
        }
    }

    private void scheduleHeartbeat(String eventId, UUID uuid, SseEmitter sseEmitter) {
        heartbeatExecutor.scheduleAtFixedRate(() -> sendHeartbeat(eventId, uuid, sseEmitter), 30, heartbeatInterval, TimeUnit.SECONDS);
    }

    private void sendHeartbeat(String eventId, UUID uuid, SseEmitter sseEmitter) {
        if (eventEmitters.getOrDefault(eventId, Collections.emptyMap()).containsKey(uuid)) {
            try {
                sseEmitter.send(SseEmitter.event().name("ping").data("heartbeat"));
            } catch (IOException e) {
                System.out.printf("Heartbeat failed for client %s on event %s: %s%n", uuid, eventId, e);
                logger.error("Heartbeat failed for client {} on event {}:", uuid, eventId, e);
                cleanupEmitter(eventId, uuid);
            }
        }
    }
}
