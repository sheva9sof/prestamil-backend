package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.util.Encryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class PasswordMigrationComponent {

    private static final Logger log = LoggerFactory.getLogger(PasswordMigrationComponent.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Encryptor encryptor;

    public PasswordMigrationComponent(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, Encryptor encryptor) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.encryptor = encryptor;
    }

    // BCrypt hashes always start with "$2". Anything else is a legacy AES/ECB ciphertext.
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateAesPasswordsToBcrypt() {
        List<Usuario> usuarios = usuarioRepository.findAll();
        int migrated = 0;
        for (Usuario usuario : usuarios) {
            String stored = usuario.getPassword();
            if (stored != null && !stored.startsWith("$2")) {
                String plaintext = encryptor.decrypt(stored);
                if (plaintext != null) {
                    usuario.setPassword(passwordEncoder.encode(plaintext));
                    usuarioRepository.save(usuario);
                    migrated++;
                } else {
                    log.warn("No se pudo desencriptar la contraseña del usuario '{}' (id={}); se omite",
                            usuario.getNombreUsuario(), usuario.getId());
                }
            }
        }
        if (migrated > 0) {
            log.info("Migración completada: {} usuario(s) migrados de AES/ECB a BCrypt", migrated);
        }
    }
}
