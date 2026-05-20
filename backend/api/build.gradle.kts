plugins {
    `java-library`
}

dependencies {
    api(project(":core"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)

    testImplementation(libs.spring.boot.starter.test)
}
