import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val exposed_version: String by project
val log4j_version: String by project

plugins {
    kotlin("jvm") version "1.7.10"
    application
}

group = "ru.fbear"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.inmo:tgbotapi:3.3.0")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.xerial:sqlite-jdbc:3.39.2.1")
    implementation("org.apache.logging.log4j:log4j-api:$log4j_version")
    implementation("org.apache.logging.log4j:log4j-core:$log4j_version")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4j_version")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "16"
}

application {
    mainClass.set("MainKt")
}