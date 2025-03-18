package com.demo.java_event.service;

import com.demo.java_event.model.Reaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ReactionService {
    private static final Logger logger = LoggerFactory.getLogger(ReactionService.class);

    // Map to store SseEmitters by event ID and client UUID
    private final Map<String, Map<UUID, SseEmitter>> eventEmitters = new ConcurrentHashMap<>();

    // Map to buffer messages by event ID and client UUID
    private final Map<String, Map<UUID, List<Reaction>>> eventMessageBuffers = new ConcurrentHashMap<>();

    // Scheduled executor for heartbeat tasks
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);

    /**
     * Saves a reaction and sends it to all subscribers of the event.
     */
    public String saveReaction(final String eventId, final Reaction reaction) {
        // Get all emitters for the event
        Map<UUID, SseEmitter> emitters = eventEmitters.get(eventId);
        if (emitters != null) {
            emitters.forEach((uuid, sseEmitter) -> {
                try {
                    logger.info("Sending reaction to client {} for event {}", uuid, eventId);
                    sseEmitter.send(reaction);
                } catch (IOException e) {
                    logger.error("IOException for client {}: {}", uuid, e.getMessage());
                    bufferMessage(eventId, uuid, reaction);
                    cleanupEmitter(eventId, uuid); // Clean up the emitter
                } catch (IllegalStateException e) {
                    if (e.getMessage().contains("ResponseBodyEmitter has already completed")) {
                        logger.error("Emitter already completed for client {}: {}", uuid, e.getMessage());
                        cleanupEmitter(eventId, uuid); // Clean up the emitter
                    }
                } catch (Exception e) {
                    logger.error("Exception for client {}: {}", uuid, e.getMessage());
                    bufferMessage(eventId, uuid, reaction);
                    cleanupEmitter(eventId, uuid); // Clean up the emitter
                }
            });
        }
        return "";
    }

    /**
     * Subscribes a client to an event and returns an SseEmitter.
     */
    public SseEmitter subscribe(final String eventId, final UUID uuid) {
        SseEmitter sseEmitter = new SseEmitter(30_000L); // 30-second timeout

        // Add the emitter to the event's emitter map
        eventEmitters.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>()).put(uuid, sseEmitter);

        // Set up completion, timeout, and error handlers
        sseEmitter.onCompletion(() -> {
            logger.info("SSE emitter completed for client {} on event {}", uuid, eventId);
            cleanupEmitter(eventId, uuid);
        });

        sseEmitter.onTimeout(() -> {
            logger.error("SSE emitter timed out for client {} on event {}", uuid, eventId);
            cleanupEmitter(eventId, uuid);
            sseEmitter.complete();
        });

        sseEmitter.onError((e) -> {
            logger.error("SSE emitter error for client {} on event {}: {}", uuid, eventId, e.getMessage());
            cleanupEmitter(eventId, uuid);
            sseEmitter.completeWithError(e);
        });

        // Resend buffered messages for this client
        resendBufferedMessages(eventId, uuid, sseEmitter);

        // Schedule heartbeat for this client
        scheduleHeartbeat(eventId, uuid, sseEmitter);

        logger.info("Client {} subscribed to event {}", uuid, eventId);
        return sseEmitter;
    }

    /**
     * Cleans up the emitter and buffered messages for a client.
     */
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

    /**
     * Buffers a message for a client if sending fails.
     */
    private void bufferMessage(String eventId, UUID uuid, Reaction reaction) {
        eventMessageBuffers.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(reaction);
    }

    /**
     * Resends buffered messages to a client.
     */
    private void resendBufferedMessages(String eventId, UUID uuid, SseEmitter sseEmitter) {
        Map<UUID, List<Reaction>> messageBuffer = eventMessageBuffers.get(eventId);
        if (messageBuffer != null) {
            List<Reaction> bufferedMessages = messageBuffer.get(uuid);
            if (bufferedMessages != null) {
                bufferedMessages.forEach(reaction -> {
                    try {
                        logger.info("Resending buffered message to client {} for event {}", uuid, eventId);
                        sseEmitter.send(reaction);
                    } catch (IOException e) {
                        logger.error("Failed to resend buffered message to client {}: {}", uuid, e.getMessage());
                    }
                });
                bufferedMessages.clear();
            }
        }
    }

    /**
     * Schedules a heartbeat for a client.
     */
    private void scheduleHeartbeat(String eventId, UUID uuid, SseEmitter sseEmitter) {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (eventEmitters.getOrDefault(eventId, Collections.emptyMap()).containsKey(uuid)) {
                try {
                    sseEmitter.send(SseEmitter.event().name("ping").data("heartbeat"));
                } catch (IOException e) {
                    logger.error("Heartbeat failed for client {} on event {}: {}", uuid, eventId, e.getMessage());
                    cleanupEmitter(eventId, uuid); // Clean up the emitter
                }
            }
        }, 10, 10, TimeUnit.SECONDS); // Send heartbeat every 10 seconds
    }
}
