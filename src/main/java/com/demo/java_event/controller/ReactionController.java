package com.demo.java_event.controller;

import com.demo.java_event.model.Reaction;
import com.demo.java_event.service.ReactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/reactions")
public class ReactionController {
    @Autowired
    private ReactionService reactionService;

    @PostMapping
    public ResponseEntity<Void> saveReaction(@RequestBody Reaction reaction) throws Exception {
        reactionService.saveReaction(reaction);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/subscribe")
    public SseEmitter subscribe(@RequestParam final UUID sessionId) {
        System.out.println(sessionId);
        return reactionService.subscribe(sessionId);
    }
}
