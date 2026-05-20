plugins {
    `java-library`
}

dependencies {
    api(project(":core"))
    implementation(libs.spring.boot.starter.web)
    // Google Health API + webhook clients wired in later (libs:
    // google-api-client, google-auth-library). Kept out of scope for IMPL-00.

    testImplementation(libs.spring.boot.starter.test)
}
