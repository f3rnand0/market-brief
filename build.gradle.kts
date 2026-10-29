plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    application
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.marketbrief"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion = "3.6.0"
val langchain4jVersion = "1.23.0"

dependencies {
    implementation(platform("dev.langchain4j:langchain4j-bom:$langchain4jVersion"))
    implementation("dev.langchain4j:langchain4j-core")
    implementation("dev.langchain4j:langchain4j-open-ai")
    implementation("com.charleskorn.kaml:kaml:0.104.0")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.slf4j:slf4j-api:2.0.19")
    implementation("ch.qos.logback:logback-classic:1.6.5")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("com.marketbrief.ApplicationKt")
}

tasks.register<JavaExec>("smokeExtraction") {
    group = "verification"
    description = "Runs one extraction against the configured LLM (manual smoke run)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.marketbrief.ExtractionSmokeKt")
}

tasks.test {
    useJUnitPlatform()
}

ktlint {
    version.set("1.8.0")
}
