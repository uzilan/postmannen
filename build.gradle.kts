plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("com.gradleup.shadow") version "9.6.0"
    application
}

group = "postmannen"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("io.ktor:ktor-client-cio:3.5.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.1")

    implementation("io.ktor:ktor-server-core:3.5.1")
    implementation("io.ktor:ktor-server-cio:3.5.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.1")
    implementation("io.ktor:ktor-server-swagger:3.5.1")
    implementation("io.ktor:ktor-server-routing-openapi:3.5.1")
    implementation("ch.qos.logback:logback-classic:1.5.16")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.ktor:ktor-server-test-host:3.5.1")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.5.1")
    testImplementation("io.ktor:ktor-client-mock:3.5.1")
}

application {
    mainClass.set("postmannen.server.ServerMainKt")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("postmannen")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "postmannen.server.ServerMainKt"
    }
}

tasks.test {
    useJUnitPlatform()
}
