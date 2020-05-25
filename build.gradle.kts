

group = "no.nav"
version = "0.0.1"

// Common
val kotlinVersion = "1.3.72"
val ktorVersion = "1.3.2"
val kotlinxVersion = "1.3.6"
val jacksonVersion = "2.11.0"
val konfigVersion = "1.6.10.0"

// Oauth2
val nimbusJoseVersion = "8.3"
val caffeineVersion = "2.8.4"
// Log
val apacheCommonsVersion = "3.10"
val logstashEncoderVersion = "6.3"
val logbackVersion = "1.2.3"
val ioPrometheusVersion = "0.9.0"
val kotlinloggingVersion = "1.7.9"

// Test
val spek = "2.0.8"
val kluentVersion = "1.61"
val wiremockVersion = "2.26.3"
val platformRunner = "1.5.1"

val mainClassName = "no.nav.dingser.DingserKt"

plugins {
    kotlin("jvm") version "1.3.72"
    java
    id("org.jmailen.kotlinter") version "2.3.2"
    id("com.github.ben-manes.versions") version "0.28.0"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://dl.bintray.com/kotlin/ktor")
    maven(url = "https://kotlin.bintray.com/kotlinx")
    maven(url = "http://packages.confluent.io/maven/")
}

tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = mainClassName
    }
    create("printVersion") {
        println(project.version)
    }
    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek")
        }
        testLogging.events("passed", "skipped", "failed")
    }
}

dependencies {
    implementation (kotlin("stdlib"))
    implementation ("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation ("io.ktor:ktor-client-okhttp:$ktorVersion")

    implementation ("io.ktor:ktor-client-okhttp:$ktorVersion")

    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation ("io.ktor:ktor-client-cio:$ktorVersion")
    implementation ("io.ktor:ktor-server-netty:$ktorVersion")
    implementation ("io.ktor:ktor-jackson:$ktorVersion")
    implementation ("io.ktor:ktor-client-core:$ktorVersion")
    implementation ("io.ktor:ktor-client-apache:$ktorVersion")
    implementation ("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-freemarker:$ktorVersion")

    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")
    implementation ("com.natpryce:konfig:$konfigVersion")

    implementation ("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation ("org.apache.commons:commons-lang3:$apacheCommonsVersion")

    // Bruk Nimbus
    implementation ("com.nimbusds:nimbus-jose-jwt:$nimbusJoseVersion")
    implementation("com.nimbusds:oauth2-oidc-sdk:${nimbusJoseVersion}")

    implementation ("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    implementation ("ch.qos.logback:logback-classic:$logbackVersion")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxVersion")

    implementation ("no.nav.security:mock-oauth2-server:0.1.33")

    implementation ("io.prometheus:simpleclient_hotspot:$ioPrometheusVersion")
    implementation ("io.prometheus:simpleclient_common:$ioPrometheusVersion")
    implementation ("io.github.microutils:kotlin-logging:$kotlinloggingVersion")

    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spek") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly ("org.spekframework.spek2:spek-runner-junit5:$spek") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.junit.platform:junit-platform-runner:$platformRunner")
    testImplementation ("com.github.tomakehurst:wiremock:$wiremockVersion")
}
