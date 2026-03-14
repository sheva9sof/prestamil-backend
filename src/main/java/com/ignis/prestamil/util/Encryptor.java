package com.ignis.prestamil.util;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

@Component
public class Encryptor {
    
    private SecretKeySpec secretKey;
    private Cipher cipher;
    private String secret = "$L@v@yS3c@Ignis2023_";

    public Encryptor() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(secret.getBytes(StandardCharsets.UTF_8));
            secretKey = new SecretKeySpec(keyBytes, "AES");
            cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException("Error al inicializar el encriptador", e);
        }
    }

    public String encrypt(String plaintext) {
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getMimeEncoder().encodeToString(encryptedBytes);
        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            return null;
        }
    }

    public String decrypt(String ciphertext) {
        try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] encryptedBytes = Base64.getMimeDecoder().decode(ciphertext);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            return null;
        }
    }
    public static void main(String[] args) {
        Encryptor encryptor = new Encryptor();
        String plaintext = "CfLvst1duF+e/+4gh87eJw==";
        String ciphertext = encryptor.decrypt(plaintext);
        System.out.println(ciphertext);
        String decryptedText = encryptor.decrypt(ciphertext);
        System.out.println(decryptedText);
    }
}
