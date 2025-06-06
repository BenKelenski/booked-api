plugins {
    application
    kotlin("jvm") version "2.1.20"
}

application { mainClass.set("dev.benkelenski.booked.AppKt") }

group = "org.example"

version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
    // http4k
    implementation(platform("org.http4k:http4k-bom:6.9.0.0"))
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-format-moshi")
    implementation("org.http4k:http4k-server-jetty")
    implementation("org.http4k:http4k-config")
    // DB
    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.61.0")
    runtimeOnly("org.jetbrains.exposed:exposed-jdbc:0.61.0")
    runtimeOnly("org.postgresql:postgresql:42.7.2")
    // logging
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    // Auth
    implementation("com.auth0:java-jwt:4.5.0")
    implementation("com.auth0:jwks-rsa:0.22.1")

    testImplementation("org.http4k:http4k-testing-kotest")
    testImplementation("org.testcontainers:postgresql:1.21.0")
    //    testImplementation("io.mockk:mockk:1.14.2")
    //    testImplementation("com.h2database:h2:2.3.232")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(21) }
