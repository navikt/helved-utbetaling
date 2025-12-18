package utsjekk

import TestRuntime
import models.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.SakKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class KafkaTest {

    @Test
    fun `can save key`() {
        val uid = UtbetalingId(UUID.randomUUID())
        val key = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        TestRuntime.topics.utbetaling.produce(uid.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid,
                sakId = sid,
                behandlingId = bid,
                originalKey = key,
                stønad = StønadTypeDagpenger.DAGPENGER,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(1.jun, 2.jun, 100u, 100u)
            }
        }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER))
            .with(SakKey(sid, Fagsystem.DAGPENGER)) {
                assertEquals(it, setOf(uid))
            }
    }

    @Test
    fun `can aggregate key`() {
        val uid = UtbetalingId(UUID.randomUUID())
        val uid2 = UtbetalingId(UUID.randomUUID())
        val key = UUID.randomUUID().toString()
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        TestRuntime.topics.utbetaling.produce(uid.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid,
                sakId = sid,
                behandlingId = bid,
                originalKey = key,
                stønad = StønadTypeDagpenger.DAGPENGER,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(1.jun, 2.jun, 100u, 100u)
            }
        }
        TestRuntime.topics.utbetaling.produce(uid2.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid2,
                sakId = sid,
                behandlingId = bid,
                originalKey = key,
                stønad = StønadTypeDagpenger.DAGPENGER,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(10.jun, 20.jun, 1000u, 1000u)
            }
        }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), 2)
            .with(SakKey(sid, Fagsystem.DAGPENGER), 0) {
                assertEquals(it, setOf(uid))
            }
            .with(SakKey(sid, Fagsystem.DAGPENGER), 1) {
                assertEquals(it, setOf(uid, uid2))
            }
    }
}

private fun utbetaling(
    action: Action,
    uid: UtbetalingId,
    sakId: SakId = SakId("$nextInt"),
    behandlingId: BehandlingId = BehandlingId("$nextInt"),
    originalKey: String = uid.id.toString(),
    førsteUtbetalingPåSak: Boolean = true,
    lastPeriodeId: PeriodeId = PeriodeId(),
    periodetype: Periodetype = Periodetype.UKEDAG,
    stønad: Stønadstype = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
    personident: Personident = Personident(""),
    vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
    beslutterId: Navident = Navident(""),
    saksbehandlerId: Navident = Navident(""),
    avvent: Avvent? = null,
    fagsystem: Fagsystem = Fagsystem.AAP,
    perioder: () -> List<Utbetalingsperiode> = { emptyList() },
): Utbetaling  {
    return  Utbetaling(
        dryrun = false,
        uid = uid,
        originalKey = originalKey,
        action = action,
        førsteUtbetalingPåSak = førsteUtbetalingPåSak,
        periodetype = periodetype,
        stønad = stønad,
        sakId = sakId,
        behandlingId = behandlingId,
        lastPeriodeId = lastPeriodeId,
        personident = personident,
        vedtakstidspunkt = vedtakstidspunkt,
        beslutterId = beslutterId,
        saksbehandlerId = saksbehandlerId,
        avvent = avvent,
        fagsystem = fagsystem,
        perioder = perioder(),
    )
}

private fun periode(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt = 123u,
    vedtakssats: UInt? = null,
    betalendeEnhet: NavEnhet? = null,
) = listOf(
    Utbetalingsperiode(
        fom = fom,
        tom = tom,
        beløp = beløp,
        vedtakssats = vedtakssats,
        betalendeEnhet = betalendeEnhet,
    )
)

var nextInt: Int = 0
    get() = field++

val Int.jun: LocalDate get() = LocalDate.of(2024, 6, this)
