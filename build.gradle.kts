plugins {
    kotlin("jvm") version "2.4.20"
    application
}

group = "org.skasti"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    implementation("com.github.ajalt.mordant:mordant:3.1.0")
    implementation("org.jline:jline-terminal:3.30.17")
    implementation("org.jline:jline-terminal-jni:3.30.17")
    implementation("org.jline:jline-reader:3.30.17")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("org.skasti.MainKt")
}

distributions {
    main {
        contents {
            from("README.md", "LICENSE")
        }
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
