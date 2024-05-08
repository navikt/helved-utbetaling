package libs.utils

object Resource {
    fun read(file: String): String {
        return this::class.java.getResource(file)!!.openStream().bufferedReader().readText()
    }
}
