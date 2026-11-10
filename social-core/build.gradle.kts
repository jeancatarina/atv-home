plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Pure JVM: the Social rules, room and protocol never see Android or Compose. The compiler enforces it.
kotlin { jvmToolchain(17) }

dependencies {
    api(libs.nanohttpd)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
}

// Develop and demo the phone side without the TV: ./gradlew :social-core:devRoom --args="--bots 3"
tasks.register<JavaExec>("devRoom") {
    group = "application"
    description = "Runs a real Social room and server on this computer, with bots and keyboard commands."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.jean.atvhome.social.DevRoomKt")
    workingDir = rootDir
    standardInput = System.`in`
}
