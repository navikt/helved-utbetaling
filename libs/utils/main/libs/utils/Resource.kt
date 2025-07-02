package libs.utils

import java.net.URL

object Resource {
    fun read(file: String): String {
        return get(file).openStream().bufferedReader().readText()
    }

    fun get(path: String): URL {
        return this::class.java.getResource(path) ?: error("Resource $path not found")
    }
}
