package com.demo.java_event.controller;

import com.demo.java_event.model.Reaction;
import com.demo.java_event.service.ReactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@RestController
@RequestMapping("/reactions")
public class ReactionController {
    @Autowired
    private ReactionService reactionService;

    @PostMapping
    public ResponseEntity<Void> saveReaction(@RequestBody Reaction reaction) {
        reactionService.saveReaction("event1", reaction);
        return ResponseEntity.ok().build();
    }


    @GetMapping(value = "/subscribe")
    public SseEmitter subscribe(@RequestParam final UUID sessionId) {
        return reactionService.subscribe("event1", sessionId);
    }
}
