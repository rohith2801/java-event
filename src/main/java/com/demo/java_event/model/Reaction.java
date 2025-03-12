package com.demo.java_event.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Reaction implements Serializable {
    private Long id;
    private String eventId;
    private String emoji;
    private String userId;
    private LocalDateTime ts;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }

    @Override
    public String toString() {
        return "Reaction{" +
                "id=" + id +
                ", eventId='" + eventId + '\'' +
                ", emoji='" + emoji + '\'' +
                ", userId='" + userId + '\'' +
                ", ts=" + ts +
                '}';
    }
}
