package utsjekk.iverksetting.utbetalingsoppdrag.bdd

import utsjekk.iverksetting.BehandlingId

typealias Table = List<List<String>>

data class DataTable(
    val input: Table,
    val expected: Table,
)

abstract class WithBehandlingId(open val behandlingId: BehandlingId)

fun <T : WithBehandlingId> Table.into(parser: (Iterator<String>) -> T): List<T> {
    return map { cols ->
        val iter = cols.iterator()
        parser(iter).also {
            if (iter.hasNext()) {
                val remaining = buildList<String> { while (iter.hasNext()) iter.next() }
                error("Found more columns in the iterator: $remaining")
            }
        }
    }
}

object Csv {
    fun read(filename: String): DataTable {
        val file = requireNotNull(this::class.java.getResource(filename)) { "file not found $filename" }
        val lines = file.openStream().bufferedReader().readLines()
        val input = lines
            .takeIf { it.first() == "# INPUT" }
            ?.drop(2) // drop input + headers
            ?.takeWhile { it != "" } // parse until empty line
            ?: error("First line was not '# INPUT'")

        val expected = lines.dropWhile { it != "# EXPECTED" }
            .drop(2) // drop expected + headers

        val inputTable = input.map { row ->
            row.split('|')
                .map { col -> col.trim() }
                .drop(1) // drop empty string before first |
                .dropLast(1) // drop empty string after last |
        }

        val expectedTable = expected.map { row ->
            row.split('|')
                .map { col -> col.trim() }
                .drop(1) // drop empty string before first |
                .dropLast(1) // drop empty string after last |
        }

        return DataTable(inputTable, expectedTable)
    }
}

