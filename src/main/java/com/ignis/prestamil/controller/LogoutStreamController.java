package com.ignis.prestamil.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/auth/stream")
public class LogoutStreamController {

    private final ConcurrentHashMap<String, SseEmitter> userEmitters = new ConcurrentHashMap<>();

    @GetMapping("/logout")
    public SseEmitter streamLogout(@RequestParam String username) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        this.userEmitters.put(username, emitter);

        emitter.onCompletion(() -> this.userEmitters.remove(username, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            this.userEmitters.remove(username, emitter);
        });

        return emitter;
    }

    public void sendForceLogoutEvent(String username) {
        SseEmitter emitter = this.userEmitters.get(username);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("force-logout").data("You have been logged out from another location."));
                emitter.complete();
            } catch (IOException e) {
                this.userEmitters.remove(username, emitter);
            }
        }
    }

    public void sendTurnoCerradoEvent(String username, String message) {
        SseEmitter emitter = this.userEmitters.get(username);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("turno-cerrado").data(message));
                emitter.complete();
            } catch (IOException e) {
                this.userEmitters.remove(username, emitter);
            }
        }
    }

    public List<String> getConnectedUsers() {
        return new ArrayList<>(this.userEmitters.keySet());
    }
}
