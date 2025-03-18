package com.demo.java_event.service;

import com.demo.java_event.model.Reaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ReactionService {
    private static final Logger logger = LoggerFactory.getLogger(ReactionService.class);

    private final List<Reaction> reactions = new CopyOnWriteArrayList<>();
    private final Map<UUID, SseEmitter> sseEmitters = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();


    public String saveReaction(final Reaction reaction) {
//        reaction.setTs(LocalDateTime.now());
        reactions.add(reaction);

        sseEmitters.forEach((uuid, sseEmitter) -> {
            try {
                logger.info("Sending message to UUID {} {}", uuid, reaction);
                sseEmitter.send(reaction);
            } catch (IOException e) {
                System.out.println("ioexception came...." + uuid);
                e.printStackTrace();
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("ResponseBodyEmitter has already completed")) {
                    sseEmitter.complete();
                    sseEmitters.remove(uuid);
                }
            }
        });

        System.out.println(reactions);

        return "";
    }

    public SseEmitter subscribe(final UUID uuid) {
        SseEmitter sseEmitter = null;
        try {
            logger.info("received session id {}", uuid);

//        sseEmitters.computeIfPresent(uuid, (key, value) -> {
//            value.complete();
//            return null;
//        });

            if (sseEmitters.get(uuid) != null) {
                logger.info("Found existing uuid {}", uuid);
                SseEmitter oldSseEmitter = sseEmitters.get(uuid);
            }

            sseEmitter = new SseEmitter(30_000L);
            sseEmitters.put(uuid, sseEmitter);

            SseEmitter finalSseEmitter = sseEmitter;
            sseEmitter.onCompletion(() -> {
                logger.info("sse emitter is completed");
                sseEmitters.remove(uuid);
            });

            sseEmitter.onTimeout(() -> {
                logger.error("sse timed out");
                sseEmitters.remove(uuid);
                finalSseEmitter.complete();
            });

            sseEmitter.onError((e) -> {
                logger.error("sse error", e);
                sseEmitters.remove(uuid);
                finalSseEmitter.complete();
            });

            try {
                sseEmitter.send(SseEmitter.event().name("connected").data("connected"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            logger.error("Exception occurred while subscribing..", e);
        }
        SseEmitter finalSseEmitter1 = sseEmitter;
        executor.execute(() -> {
            try {
                while (true) {
                    finalSseEmitter1.send(SseEmitter.event().name("ping").data("heartbeat"));
                    Thread.sleep(3000); // Send every 30 seconds
                }
            } catch (Exception e) {
                finalSseEmitter1.complete();
                sseEmitters.remove(finalSseEmitter1);
            }
        });

        logger.info("Sending sse emitter for session ID: {}", uuid);
        return sseEmitter;
    }
}
