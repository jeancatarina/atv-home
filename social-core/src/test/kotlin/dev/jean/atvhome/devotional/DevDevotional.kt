package dev.jean.atvhome.devotional

import java.io.File

/** The real devotional site on this computer, for the phone page without the TV: ./gradlew :social-core:devDevotional */
fun main() {
    val file = File("build/devotional-dev.json").apply { delete() }
    val store = DevotionalStore(file)
    val server = DevotionalServer(Devotional(store.load(), onChange = store::save), { File("app/src/main/assets/devotional.html").readText() }, port = 8767)
    server.start(5000, false)
    println("up on http://127.0.0.1:8767/")
    Thread.currentThread().join()
}
