package com.demo.java_event.service;

import com.demo.java_event.model.Reaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ReactionService {
    private static final Logger logger = LoggerFactory.getLogger(ReactionService.class);

    private final List<Reaction> reactions = new CopyOnWriteArrayList<>();
    private final Map<UUID, SseEmitter> sseEmitters = new ConcurrentHashMap<>();
    private final Map<UUID, List<Reaction>> messageBuffer = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public String saveReaction(final Reaction reaction) {
        reactions.add(reaction);

        sseEmitters.forEach((uuid, sseEmitter) -> {
            try {
                logger.info("Sending message to UUID {} {}", uuid, reaction);
                sseEmitter.send(reaction);
            } catch (IOException e) {
                logger.error("IOException occurred for UUID {}: {}", uuid, e.getMessage());
                bufferMessage(uuid, reaction);
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("ResponseBodyEmitter has already completed")) {
                    sseEmitter.complete();
                    sseEmitters.remove(uuid);
                    bufferMessage(uuid, reaction);
                }
            } catch (Exception e) {
                logger.error("Exception occurred for UUID {}: {}", uuid, e.getMessage());
                bufferMessage(uuid, reaction);
            }
        });

        return "";
    }

    public SseEmitter subscribe(final UUID uuid) {
        SseEmitter sseEmitter = new SseEmitter(30_000L);
        sseEmitters.put(uuid, sseEmitter);

        sseEmitter.onCompletion(() -> {
            logger.info("SSE emitter completed for UUID {}", uuid);
            sseEmitters.remove(uuid);
        });

        sseEmitter.onTimeout(() -> {
            logger.error("SSE emitter timed out for UUID {}", uuid);
            sseEmitters.remove(uuid);
            sseEmitter.complete();
        });

        sseEmitter.onError((e) -> {
            logger.error("SSE emitter error for UUID {}: {}", uuid, e.getMessage());
            sseEmitters.remove(uuid);
            sseEmitter.completeWithError(e);
        });

        resendBufferedMessages(uuid, sseEmitter);

        executor.execute(() -> {
            try {
                while (sseEmitters.containsKey(uuid)) {
                    try {
                        sseEmitter.send(SseEmitter.event().name("ping").data("heartbeat"));
                    } catch (IOException e) {
                        logger.error("IOException while sending heartbeat to UUID {}: {}", uuid, e.getMessage());
                        sseEmitters.remove(uuid);
                        sseEmitter.completeWithError(e);
                        break;
                    }
                    Thread.sleep(10_000);
                }
            } catch (Exception e) {
                logger.error("Exception in heartbeat thread for UUID {}: {}", uuid, e.getMessage());
                sseEmitters.remove(uuid);
                sseEmitter.completeWithError(e);
            }
        });

        logger.info("Sending SSE emitter for session ID: {}", uuid);
        return sseEmitter;
    }

    private void bufferMessage(UUID uuid, Reaction reaction) {
        messageBuffer.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>()).add(reaction);
    }

    private void resendBufferedMessages(UUID uuid, SseEmitter sseEmitter) {
        List<Reaction> bufferedMessages = messageBuffer.get(uuid);
        if (bufferedMessages != null) {
            bufferedMessages.forEach(reaction -> {
                try {
                    logger.info("Resending buffered message to UUID {} {}", uuid, reaction);
                    sseEmitter.send(reaction);
                } catch (IOException e) {
                    logger.error("Failed to resend buffered message to UUID {}: {}", uuid, e.getMessage());
                }
            });

            bufferedMessages.clear();
        }
    }
}