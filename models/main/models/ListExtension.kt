package models

fun <T> List<T>.splitWhen(predicate: (T, T) -> Boolean): List<List<T>> {
    if (this.isEmpty()) return emptyList()

    return this.drop(1).fold(mutableListOf(mutableListOf(this.first()))) { acc, item ->
        val lastSublist = acc.last()
        if (predicate(lastSublist.last(), item)) {
            acc.add(mutableListOf(item))
        } else {
            lastSublist.add(item)
        }
        acc
    }.map { it.toList() }
}