package utsjekk.iverksetting.utbetalingsoppdrag

//typealias Table = List<List<String>>

class UtbetalingsgeneratorCsvTest {

//    @Test
//    fun `en periode får en lik periode`() {
//        val (input, expected) = readCsv("/csv/en_periode_får_en_lik_periode.csv")
//
//        val andeler = input.map { (_, fom, tom, beløp) ->
//            parseAndel(
//                andelId = AndelId.next(),
//                fom = fom.toLocalDate(),
//                tom = tom.toLocalDate(),
//                beløp = beløp.toInt(),
//            )
//        }
//
//        assertFalse(andeler.any { it.periodeId != null || it.forrigePeriodeId != null })
//
//        val utbetalinger = expected.map { cols ->
//            val iter = cols.iterator()
//            parseUtbetaling(
//                behandlingId = iter.next().toLong(),
//                fom = iter.next().toLocalDate(),
//                tom = iter.next().toLocalDate(),
//                opphørsdato = iter.next().let { if (it.isEmpty()) null else it.toLocalDate() },
//                beløp = iter.next().toInt(),
//                førsteUtbetSak = iter.next().toBoolean(),
//                erEndring = iter.next().toBoolean(),
//                periodeId = iter.next().let { if (it.isEmpty()) null else it.toLong() },
//                forrigePeriodeId = iter.next().let { if (it.isEmpty()) null else it.toLong() },
//            )
//            if (iter.hasNext()) {
//                error("too many columns in expected csv")
//            }
//        }
//    }


//    private fun readCsv(filename: String): Pair<Table, Table> {
//        val file = requireNotNull(this::class.java.getResource(filename)) { "file not found $filename" }
//        val lines = file.openStream().bufferedReader().readLines()
//        val input = lines
//            .takeIf { it.first() == "# INPUT" }
//            ?.drop(2) // drop input + headers
//            ?.takeWhile { it != "" } // parse until empty line
//            ?: error("First line was not '# INPUT'")
//
//        val expected = lines.dropWhile { it != "# EXPECTED" }
//            .drop(2) // drop expected + headers
//
//        val inputTable = input.map { row ->
//            row.split('|')
//                .map { col -> col.trim() }
//                .drop(1) // drop empty string before first |
//                .dropLast(1) // drop empty string after last |
//        }
//
//        val expectedTable = expected.map { row ->
//            row.split('|')
//                .map { col -> col.trim() }
//                .drop(1) // drop empty string before first |
//                .dropLast(1) // drop empty string after last |
//        }
//
//        return inputTable to expectedTable
//    }
}

//private object AndelId {
//    private var id = -1L
//    fun next() = ++id
//}
//
//private fun String.toLocalDate(): LocalDate = LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE)
//
//private fun parseAndel(
//    andelId: Long,
//    fom: LocalDate,
//    tom: LocalDate,
//    beløp: Int,
//): AndelData {
//    val stønadsdataDp = StønadsdataDagpenger(
//        stønadstype = StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR,
//        ferietillegg = null,
//    )
//
//    return AndelData(
//        id = andelId.toString(),
//        fom = fom,
//        tom = tom,
//        beløp = beløp,
//        satstype = Satstype.DAGLIG,
//        stønadsdata = stønadsdataDp,
//        periodeId = null,
//        forrigePeriodeId = null,
//    )
//}