package peisschtappern

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import models.Fagsystem
import kotlin.test.*

class AvstemmingsstatusTest {
    private val dato = LocalDate.of(2025, 8, 11)
    private val fom = LocalDateTime.of(2025, 8, 8, 0, 0)
    private val tom = LocalDateTime.of(2025, 8, 10, 23, 59, 59, 999_999_000)

    @Test
    fun `mandag inkluderer helgens oppdrag`() {
        assertEquals(fom to tom, avstemmingsperiode(dato))
    }

    @Test
    fun `helg vurderer perioden for siste virkedags kjøring`() {
        assertEquals(avstemmingsperiode(dato.minusDays(3)), avstemmingsperiode(dato.minusDays(1)))
    }

    @Test
    fun `påske bruker samme virkedagskalender som vedskiva`() {
        val (start, slutt) = avstemmingsperiode(LocalDate.of(2025, 4, 22))
        assertEquals(LocalDate.of(2025, 4, 16).atStartOfDay(), start)
        assertEquals(LocalDate.of(2025, 4, 21), slutt.toLocalDate())
    }

    @Test
    fun `alle fagsystemer vises uten aktivitet`() {
        val statuser = avstemmingsstatus(fom, tom, emptyList(), emptyList())
        assertEquals(Fagsystem.entries.toSet(), statuser.map { it.fagsystem }.toSet())
        assertTrue(statuser.all { it.status == Avstemmingsstatus.NOT_REQUIRED })
    }

    @Test
    fun `nytt fagsystem uten historisk avstemming vises som manglende`() {
        assertEquals(Avstemmingsstatus.MISSING, status(emptyList()))
    }

    @Test
    fun `data alene eller ulike kjøringsider betyr ikke fullført`() {
        assertEquals(Avstemmingsstatus.MISSING, status(listOf(melding("DATA"))))
        assertEquals(Avstemmingsstatus.MISSING, status(listOf(melding("START"), melding("DATA"), melding("AVSL", id = "annen"))))
    }

    @Test
    fun `start data og avsl med samme id og periode betyr fullført`() {
        assertEquals(Avstemmingsstatus.COMPLETED, status(kjøring(fom, tom)))
    }

    @Test
    fun `alle oppdragsnøkler må være dekket`() {
        val oppdrag = listOf("AAP" to fom.plusHours(1), "AAP" to tom.minusHours(1))
        assertEquals(Avstemmingsstatus.MISSING, status(kjøring(fom, fom.plusHours(2)), oppdrag))
        assertEquals(Avstemmingsstatus.COMPLETED, status(kjøring(fom, fom.plusHours(2)) + kjøring(tom.minusHours(2), tom, "andre"), oppdrag))
    }

    @Test
    fun `meldinger med ulik periode kan ikke fullføre samme kjøring`() {
        assertEquals(Avstemmingsstatus.MISSING, status(listOf(melding("START"), melding("DATA"), melding("AVSL", slutt = tom.minusDays(1)))))
    }

    private fun status(
        meldinger: List<AvstemmingXML>,
        oppdrag: List<Pair<String, LocalDateTime>> = listOf("AAP" to fom.plusHours(1))
    ): Avstemmingsstatus = avstemmingsstatus(fom, tom, oppdrag, meldinger)
        .single { it.fagsystem == Fagsystem.AAP }.status

    private fun kjøring(start: LocalDateTime, slutt: LocalDateTime, id: String = "id"): List<AvstemmingXML> =
        listOf("START", "DATA", "AVSL").map { melding(it, id, start, slutt) }

    private fun melding(
        type: String,
        id: String = "id",
        start: LocalDateTime = fom,
        slutt: LocalDateTime = tom
    ): AvstemmingXML {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
        return AvstemmingXML(
            AvstemmingXML.Aksjon(type, start.format(formatter), slutt.format(formatter), id, "AAP"),
            if (type == "DATA") AvstemmingXML.Periode("2025080800", "2025081023") else null
        )
    }
}
