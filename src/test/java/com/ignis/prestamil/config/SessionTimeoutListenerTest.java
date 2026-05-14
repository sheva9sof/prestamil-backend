package com.ignis.prestamil.config;

import com.ignis.prestamil.model.ParametrosSistema;
import com.ignis.prestamil.repository.ParametrosSistemaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.session.MapSession;
import org.springframework.session.events.SessionCreatedEvent;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionTimeoutListenerTest {

    @Mock
    private ParametrosSistemaRepository parametrosSistemaRepository;

    @InjectMocks
    private SessionTimeoutListener sessionTimeoutListener;

    private ParametrosSistema param6;

    @BeforeEach
    void setUp() {
        param6 = new ParametrosSistema();
        param6.setId(6);
        param6.setValorNumerico(new BigDecimal("45"));
    }

    @Test
    void setsMaxInactiveIntervalFromDb() {
        when(parametrosSistemaRepository.findById(6)).thenReturn(Optional.of(param6));

        MapSession session = new MapSession();
        SessionCreatedEvent event = new SessionCreatedEvent(this, session);

        sessionTimeoutListener.onApplicationEvent(event);

        assertEquals(Duration.ofMinutes(45), session.getMaxInactiveInterval());
    }

    @Test
    void usesDefaultOf30MinutesWhenParamMissing() {
        when(parametrosSistemaRepository.findById(6)).thenReturn(Optional.empty());

        MapSession session = new MapSession();
        SessionCreatedEvent event = new SessionCreatedEvent(this, session);

        sessionTimeoutListener.onApplicationEvent(event);

        assertEquals(Duration.ofMinutes(30), session.getMaxInactiveInterval());
    }
}
