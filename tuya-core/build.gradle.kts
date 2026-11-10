plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Pure JVM: the Tuya local protocol never sees Android or Compose, and runs against a fake hub in tests
kotlin { jvmToolchain(17) }

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
}

// One-time setup on the computer: reads keys and names from the Tuya cloud, writes tuya.json, pushes it to the TV.
// tools/tuya-setup.sh wraps it. Lives in the test source set so nothing of it reaches the APK.
tasks.register<JavaExec>("setup") {
    group = "application"
    description = "Fetches the hub's local key and the switch names from the Tuya cloud and writes tuya.json."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.jean.atvhome.tuya.CloudSetupKt")
    workingDir = rootDir
}

// A hub on this computer that speaks the same protocol: ./gradlew :tuya-core:fakeHub --args="--version 3.4"
tasks.register<JavaExec>("fakeHub") {
    group = "application"
    description = "Runs a fake Tuya Zigbee hub on this computer, for the app without the house."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.jean.atvhome.tuya.FakeHubMainKt")
    workingDir = rootDir
    standardInput = System.`in`
}

// The TV's code against the real hub, from this computer: ./gradlew :tuya-core:probe --args="--toggle Escritório"
tasks.register<JavaExec>("probe") {
    group = "application"
    description = "Connects to the real hub with build/tuya.json, reads every light and optionally switches one."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.jean.atvhome.tuya.ProbeKt")
    workingDir = rootDir
}
