package com.gte619n.healthfitness.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// Full-stack integration: HTTP → dev-mode auth filter → UserProvisioningFilter
// → real Firestore-backed UserRepository → emulator. The test profile gives
// us dev-mode bypass; @DynamicPropertySource flips firestore-enabled back on
// and points it at the emulator container.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class UserProvisioningFilterIntegrationTest {

    @Container
    static FirestoreEmulatorContainer emulator = new FirestoreEmulatorContainer(
        DockerImageName.parse("gcr.io/google.com/cloudsdktool/cloud-sdk:emulators")
    );

    @DynamicPropertySource
    static void firestoreProps(DynamicPropertyRegistry registry) {
        registry.add("app.persistence.firestore-enabled", () -> "true");
        registry.add("app.gcp.firestore-emulator-host", emulator::getEmulatorEndpoint);
    }

    @Autowired MockMvc mvc;
    @Autowired Firestore firestore;

    @BeforeEach
    void clearUsers() throws Exception {
        for (var ref : firestore.collection("users").listDocuments()) {
            ref.delete().get();
        }
    }

    @Test
    void firstAuthenticatedRequestProvisionsUser() throws Exception {
        mvc.perform(get("/api/me").header("X-Dev-User", "u-int-1"))
           .andExpect(status().isOk());

        List<QueryDocumentSnapshot> docs = firestore.collection("users").get().get().getDocuments();
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getId()).isEqualTo("u-int-1");
    }

    @Test
    void repeatedRequestDoesNotProvisionASecondUser() throws Exception {
        mvc.perform(get("/api/me").header("X-Dev-User", "u-int-2")).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("X-Dev-User", "u-int-2")).andExpect(status().isOk());

        List<QueryDocumentSnapshot> docs = firestore.collection("users").get().get().getDocuments();
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getId()).isEqualTo("u-int-2");
    }
}
