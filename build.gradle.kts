import groovy.json.JsonSlurper
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    kotlin("jvm") version "2.1.20"
    id("com.gradleup.shadow") version "8.3.6"
}

application { mainClass = "dev.benkelenski.booked.AppKt" }

group = "dev.benkelenski"

version = "1.0"

repositories { mavenCentral() }

dependencies {
    // http4k
    implementation(platform("org.http4k:http4k-bom:6.9.0.0"))
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-format-moshi")
    implementation("org.http4k:http4k-server-jetty")
    implementation("org.http4k:http4k-client-okhttp")
    implementation("org.http4k:http4k-config")
    // DB
    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.61.0")
    runtimeOnly("org.jetbrains.exposed:exposed-jdbc:0.61.0")
    runtimeOnly("org.postgresql:postgresql:42.7.2")
    // logging
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    // Auth
    implementation("com.auth0:java-jwt:4.5.0")
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("at.favre.lib:bcrypt:0.10.2")
    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:2.9.0")
    implementation("com.sksamuel.hoplite:hoplite-hocon:2.9.0")

    // Testing
    testImplementation("org.http4k:http4k-testing-kotest")
    testImplementation("org.testcontainers:postgresql:1.21.0")
    testImplementation(kotlin("test"))
}

// lazily fetch Colima’s IP once
val colimaHost: String by lazy {
    val proc =
        ProcessBuilder("colima", "ls", "-j").redirectErrorStream(true).start().apply { waitFor() }
    val json = JsonSlurper().parseText(proc.inputStream.bufferedReader().readText()) as Map<*, *>
    json["address"] as String
}

tasks.test {
    // point Docker client at Colima’s socket
    environment(
        "DOCKER_HOST",
        "unix://${System.getProperty("user.home")}/.colima/default/docker.sock",
    )
    // tell Testcontainers to bind inside container to /var/run/docker.sock
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    // tell Testcontainers the host address of the Colima VM
    environment("TESTCONTAINERS_HOST_OVERRIDE", colimaHost)

    testLogging {
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        // show System.out/System.err for every test
        showStandardStreams = true
    }

    useJUnitPlatform()
}

kotlin { jvmToolchain(21) }
