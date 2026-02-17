plugins {
    java
    application
    id("com.gradleup.shadow") version "9.3.1"
}

group = "org.powergrid"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass.set("org.powergrid.Main")
}

val pekkoVersion     = "1.1.3"
val pekkoHttpVersion = "1.1.0"
val jacksonVersion   = "2.18.2"
val junitVersion     = "5.11.4"
val logbackVersion   = "1.5.12"

repositories {
    mavenCentral()
}

dependencies {
    // Pekko (Scala 3 binary)
    implementation("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-stream_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-http_3:$pekkoHttpVersion")
    implementation("org.apache.pekko:pekko-http-spray-json_3:$pekkoHttpVersion")
    implementation("org.apache.pekko:pekko-slf4j_3:$pekkoVersion")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3:$pekkoVersion")
    testImplementation("org.apache.pekko:pekko-stream-testkit_3:$pekkoVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("powergrid-server")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}
