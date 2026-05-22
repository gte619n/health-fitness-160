plugins {
    `java-library`
}

dependencies {
    api(project(":core"))
    implementation(libs.google.cloud.firestore)
    implementation(libs.spring.boot.autoconfigure)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.gcloud)
    // Spring Boot 3.5 ships JUnit Platform 1.12 but Gradle's embedded test
    // worker uses an older launcher. Pin the launcher so versions align.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
