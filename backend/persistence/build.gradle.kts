plugins {
    `java-library`
}

dependencies {
    api(project(":core"))
    // Firestore client wired in later (see libs.versions.toml: google-cloud-firestore,
    // spring-cloud-gcp-firestore). Kept out of scope for IMPL-00 to avoid pulling
    // GCP credentials into the unit-test path.

    testImplementation(libs.spring.boot.starter.test)
}
