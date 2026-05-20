plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":api"))
    implementation(project(":core"))
    implementation(project(":persistence"))
    implementation(project(":integrations"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    // OAuth2 client + security wired up but inert until IMPL-XX adds login flow.
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.client)
    // Spring Cloud GCP starter for Firestore. Disabled in tests via
    // application-test.yml so unit tests don't need GCP credentials.
    implementation(libs.spring.cloud.gcp.firestore)

    testImplementation(libs.spring.boot.starter.test)
}

springBoot {
    mainClass.set("com.gte619n.healthfitness.HealthFitnessApplication")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}
