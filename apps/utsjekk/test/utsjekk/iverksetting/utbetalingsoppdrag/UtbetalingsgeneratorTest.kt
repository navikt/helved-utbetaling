package utsjekk.iverksetting.utbetalingsoppdrag

import org.junit.jupiter.api.Test

class UtbetalingsgeneratorTest {

    @Test
    fun `en periode får en lik periode`() {
    }


    fun <T> readCsv(filename: String, mapper: (String) -> T): List<T> {
        return this::class.java.getResource(filename)!!.openStream().bufferedReader().readLines().drop(1).map {
            mapper(it)
        }
    }
}
