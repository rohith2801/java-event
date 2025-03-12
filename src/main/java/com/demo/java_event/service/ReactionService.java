package com.demo.java_event.service;

import com.demo.java_event.model.Reaction;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ReactionService {
    private final List<Reaction> reactions = new CopyOnWriteArrayList<>();
    private final Map<UUID, SseEmitter> sseEmitters = new ConcurrentHashMap<>();

    public String saveReaction(final Reaction reaction) {
//        reaction.setTs(LocalDateTime.now());
        reactions.add(reaction);

        sseEmitters.forEach((uuid, sseEmitter) -> {
            try {
                sseEmitter.send(reaction);
            } catch (IOException e) {
                System.out.println("ioexception came...." + uuid);
                e.printStackTrace();
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("ResponseBodyEmitter is already set complete")) {
                    sseEmitters.remove(uuid);
                }
            }
        });

        System.out.println(reactions);

        return "";
    }

    public SseEmitter subscribe(final UUID uuid) {
        SseEmitter sseEmitter;

        if (sseEmitters.get(uuid) != null) {
            sseEmitter = sseEmitters.get(uuid);
            sseEmitter.complete();
        }

        sseEmitter = new SseEmitter();
        sseEmitters.put(uuid, sseEmitter);

        return sseEmitter;
    }
}
