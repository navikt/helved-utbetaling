package libs.utils

fun String?.intoUids(): List<String> {
    return this?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() } 
        ?: emptyList<String>()
}

