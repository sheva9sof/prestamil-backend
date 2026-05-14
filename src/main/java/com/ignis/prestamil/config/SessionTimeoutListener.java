package com.ignis.prestamil.config;

import com.ignis.prestamil.repository.ParametrosSistemaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.session.Session;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SessionTimeoutListener implements ApplicationListener<SessionCreatedEvent> {

    @Autowired
    private ParametrosSistemaRepository parametrosSistemaRepository;

    @Override
    public void onApplicationEvent(SessionCreatedEvent event) {
        int minutes = parametrosSistemaRepository.findById(6)
            .map(p -> p.getValorNumerico() != null ? p.getValorNumerico().intValue() : 30)
            .orElse(30);
        Session session = event.getSession();
        session.setMaxInactiveInterval(Duration.ofMinutes(minutes));
    }
}
