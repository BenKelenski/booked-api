plugins {
    kotlin("jvm") version "2.1.20"
    id("app.cash.sqldelight") version "2.1.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // http4k
    implementation(platform("org.http4k:http4k-bom:6.9.0.0"))
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-format-moshi")
    implementation("org.http4k:http4k-server-jetty")
    implementation("org.http4k:http4k-config")
    // DB
    implementation("app.cash.sqldelight:jdbc-driver:2.1.0")
    implementation("com.zaxxer:HikariCP:6.3.0")
    runtimeOnly("org.postgresql:postgresql:42.7.2")
    // logging
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation("org.http4k:http4k-testing-kotest")
    testImplementation("io.mockk:mockk:1.14.2")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("dev.benkelenski.booked")
            dialect("app.cash.sqldelight:postgresql-dialect:2.1.0")
        }
    }
}