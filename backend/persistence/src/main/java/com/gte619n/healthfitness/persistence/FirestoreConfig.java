package com.gte619n.healthfitness.persistence;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Gated on app.persistence.firestore-enabled (default true) so unit tests can
// turn the real Firestore beans off and provide an in-memory fake.
@Configuration
@ConditionalOnProperty(name = "app.persistence.firestore-enabled", havingValue = "true", matchIfMissing = true)
public class FirestoreConfig {

    @Bean
    Firestore firestore(
        @Value("${app.gcp.project-id}") String projectId,
        @Value("${app.gcp.firestore-emulator-host:}") String emulatorHost
    ) {
        FirestoreOptions.Builder builder = FirestoreOptions.newBuilder()
            .setProjectId(projectId);
        if (!emulatorHost.isBlank()) {
            // setEmulatorHost configures plaintext gRPC + NoCredentials; used
            // by integration tests via @DynamicPropertySource and by dev.sh
            // when run with --emulator.
            builder.setEmulatorHost(emulatorHost);
        }
        return builder.build().getService();
    }
}
