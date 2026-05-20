plugins {
    `java-library`
}

dependencies {
    api(libs.spring.context)

    testImplementation(libs.spring.boot.starter.test)
}
