package com.gte619n.healthfitness.integrations.googlehealth;

import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Envelope encryption helper. See ADR-0004.
//
//   plaintext  →  AES-256-GCM(plaintext, dek)        → refreshTokenCiphertext
//   dek        →  KMS.encrypt(dek, kek)              → dekCiphertext
//
// refreshTokenCiphertext also carries a 12-byte GCM nonce as a prefix.
// One KMS round trip per encrypt and per decrypt.
@Component
public class KmsTokenCipher {

    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int DEK_BYTES = 32; // AES-256

    private final String kmsKeyName;
    private final SecureRandom random = new SecureRandom();

    public KmsTokenCipher(@Value("${app.googlehealth.kms-key-name}") String kmsKeyName) {
        this.kmsKeyName = kmsKeyName;
    }

    public EncryptedToken encrypt(String plaintext) {
        byte[] dek = new byte[DEK_BYTES];
        random.nextBytes(dek);

        byte[] nonce = new byte[GCM_NONCE_BYTES];
        random.nextBytes(nonce);

        byte[] ciphertextNoNonce = aesGcm(Cipher.ENCRYPT_MODE, dek, nonce,
            plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] ciphertextWithNonce = ByteBuffer.allocate(nonce.length + ciphertextNoNonce.length)
            .put(nonce).put(ciphertextNoNonce).array();

        byte[] wrappedDek = encryptDekViaKms(dek);
        // Zero the in-memory DEK as soon as we have the wrapped copy.
        java.util.Arrays.fill(dek, (byte) 0);

        return new EncryptedToken(ciphertextWithNonce, wrappedDek);
    }

    public String decrypt(EncryptedToken token) {
        byte[] dek = decryptDekViaKms(token.dekCiphertext());
        try {
            byte[] full = token.refreshTokenCiphertext();
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            byte[] body = new byte[full.length - GCM_NONCE_BYTES];
            System.arraycopy(full, 0, nonce, 0, GCM_NONCE_BYTES);
            System.arraycopy(full, GCM_NONCE_BYTES, body, 0, body.length);
            byte[] plaintext = aesGcm(Cipher.DECRYPT_MODE, dek, nonce, body);
            return new String(plaintext, StandardCharsets.UTF_8);
        } finally {
            java.util.Arrays.fill(dek, (byte) 0);
        }
    }

    private byte[] encryptDekViaKms(byte[] dek) {
        try (KeyManagementServiceClient client = KeyManagementServiceClient.create()) {
            CryptoKeyName keyName = CryptoKeyName.parse(kmsKeyName);
            EncryptResponse response = client.encrypt(keyName, ByteString.copyFrom(dek));
            return response.getCiphertext().toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("KMS encrypt failed", e);
        }
    }

    private byte[] decryptDekViaKms(byte[] wrappedDek) {
        try (KeyManagementServiceClient client = KeyManagementServiceClient.create()) {
            CryptoKeyName keyName = CryptoKeyName.parse(kmsKeyName);
            DecryptResponse response = client.decrypt(keyName, ByteString.copyFrom(wrappedDek));
            return response.getPlaintext().toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("KMS decrypt failed", e);
        }
    }

    private static byte[] aesGcm(int mode, byte[] key, byte[] nonce, byte[] in) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return cipher.doFinal(in);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("AES-GCM operation failed", e);
        }
    }

    public record EncryptedToken(byte[] refreshTokenCiphertext, byte[] dekCiphertext) {}
}
