package com.gte619n.healthfitness.persistence.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.gte619n.healthfitness.core.user.User;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// Boots the Firestore emulator in a container and exercises the real
// Firestore-backed UserRepository. Spring is not involved — we new up the
// repository directly so the test stays focused on persistence behavior.
@Testcontainers
class UserRepositoryIntegrationTest {

    @Container
    static FirestoreEmulatorContainer emulator = new FirestoreEmulatorContainer(
        DockerImageName.parse("gcr.io/google.com/cloudsdktool/cloud-sdk:emulators")
    );

    private static Firestore firestore;
    private static UserRepository repo;

    @BeforeAll
    static void setUp() {
        firestore = FirestoreOptions.newBuilder()
            .setProjectId("health-fitness-test")
            .setEmulatorHost(emulator.getEmulatorEndpoint())
            .build()
            .getService();
        repo = new UserRepository(firestore);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (firestore != null) firestore.close();
    }

    @BeforeEach
    void clearUsers() throws Exception {
        try {
            for (var ref : firestore.collection("users").listDocuments()) {
                ref.delete().get();
            }
        } catch (NotFoundException ignored) {
            // Collection might not exist yet on first run.
        }
    }

    @Test
    void saveAndFindRoundTripsUser() {
        repo.save(new User("u1", "u1@example.com", "User One", null, null, null));

        Optional<User> found = repo.findById("u1");

        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo("u1");
        assertThat(found.get().email()).isEqualTo("u1@example.com");
        assertThat(found.get().displayName()).isEqualTo("User One");
        assertThat(found.get().createdAt()).isNotNull();
        assertThat(found.get().updatedAt()).isNotNull();
    }

    @Test
    void findMissingReturnsEmpty() {
        assertThat(repo.findById("nope")).isEmpty();
    }

    @Test
    void repeatedSaveKeepsCreatedAtAndBumpsUpdatedAt() throws InterruptedException {
        repo.save(new User("u2", "first@example.com", "Two", null, null, null));
        Instant createdAt = repo.findById("u2").orElseThrow().createdAt();

        // serverTimestamp() resolution can collide if writes happen in the
        // same millisecond; the sleep makes the assertion deterministic.
        Thread.sleep(50);
        repo.save(new User("u2", "renamed@example.com", "Two", null, null, null));

        User second = repo.findById("u2").orElseThrow();
        assertThat(second.createdAt()).isEqualTo(createdAt);
        assertThat(second.updatedAt()).isAfterOrEqualTo(createdAt);
        assertThat(second.email()).isEqualTo("renamed@example.com");
    }
}
