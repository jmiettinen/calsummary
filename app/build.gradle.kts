import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jlleitschuh.gradle.ktlint") version "12.0.3"
    application
}

val javaVersionToUse = JavaVersion.VERSION_17

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersionToUse.majorVersion))
        vendor.set(JvmVendorSpec.AMAZON)
    }
    // IDEA Is having a bad day with this.
//    sourceCompatibility = javaVersionToUse
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xjsr305=strict"))
        jvmTarget.set(JvmTarget.fromTarget(javaVersionToUse.majorVersion))
    }
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use the Kotlin JUnit 5 integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    // Use the JUnit 5 integration.
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.1")

    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("com.github.ajalt.clikt:clikt:4.2.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")
    implementation("org.mnode.ical4j:ical4j:3.2.14")
}

tasks.withType<ShadowJar> {
    this.archiveBaseName.set("calsummary")
    this.archiveClassifier.set("")
    this.archiveVersion.set("")
}

application {
    mainClass.set("cal.summary.AppKt")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
