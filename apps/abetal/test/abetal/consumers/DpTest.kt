package abetal.consumers

import abetal.*
import abetal.models.dpUId
import abetal.models.Rettighetstype
import abetal.models.Utbetalingstype
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import models.*
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import kotlin.time.Duration.Companion.milliseconds
import org.junit.jupiter.api.Test

internal class DpTest {

    @Test
    fun `1 meldekort i 1 utbetalinger blir til 1 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = dpUId(sid.id, meldeperiode, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestTopics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = "132460781",
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(listOf(
                DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DPORAS"),
            ))
        )
        TestTopics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestTopics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
                }
                assertEquals(expected, it)
            }

        TestTopics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("DPORAS", a.kodeKlassifik)
                    assertEquals(553, a.sats.toLong())
                    assertEquals(1077, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
            }

        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 1)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid), index = 0)
    }

    @Test
    fun `2 meldekort i 1 utbetalinger blir til 2 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestTopics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 2377u,
                    utbetaltBeløp = 779u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(listOf(
                DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DPORAS"),
                DetaljerLinje(bid.id, 7.jul21, 20.jul21, 2377u, 779u, "DPORAS"),
            ))
        )
        TestTopics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestTopics.utbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
                }
                assertEquals(expected, it)
            }
            .has(uid2.id.toString())
            .with(uid2.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
                }
                assertEquals(expected, it)
            }
        TestTopics.oppdrag.assertThat()
            .has(originalKey, size = 1)
            .with(originalKey, index = 0) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(2, it.oppdrag110.oppdragsLinje150s.size)
                val førsteLinje = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(førsteLinje.refDelytelseId)
                assertEquals("NY", førsteLinje.kodeEndringLinje)
                assertEquals(bid.id, førsteLinje.henvisning)
                assertEquals("DPORAS", førsteLinje.kodeKlassifik)
                assertEquals(553, førsteLinje.sats.toLong())
                assertEquals(1077, førsteLinje.vedtakssats157.vedtakssats.toLong())
                val andreLinje = it.oppdrag110.oppdragsLinje150s[1]
                assertEquals("NY", andreLinje.kodeEndringLinje)
                assertEquals(bid.id, andreLinje.henvisning)
                assertEquals("DPORAS", andreLinje.kodeKlassifik)
                assertEquals(779, andreLinje.sats.toLong())
                assertEquals(2377, andreLinje.vedtakssats157.vedtakssats.toLong())
            }
        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 0) {
                assertEquals(it, setOf(uid1))
            }
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 1) {
                assertEquals(it, setOf(uid1, uid2))
            }
    }

    @Test
    fun `2 meldekort i ett med 2 klassekoder hver blir til 4 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid2 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG)
        val uid3 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.PERMITTERING_ORDINÆR)
        val uid4 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.PERMITTERING_ORDINÆR_FERIETILLEGG)

        TestTopics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 1000u,
                    utbetaltBeløp = 1000u,
                    rettighetstype = Rettighetstype.Ordinær,
                    utbetalingstype = Utbetalingstype.Dagpenger,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 100u,
                    utbetaltBeløp = 100u,
                    rettighetstype = Rettighetstype.Ordinær,
                    utbetalingstype = Utbetalingstype.DagpengerFerietillegg,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 600u,
                    utbetaltBeløp = 600u,
                    rettighetstype = Rettighetstype.Permittering,
                    utbetalingstype = Utbetalingstype.Dagpenger,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 300u,
                    utbetaltBeløp = 300u,
                    rettighetstype = Rettighetstype.Permittering,
                    utbetalingstype = Utbetalingstype.DagpengerFerietillegg,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(listOf(
                DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1000u, 1000u, "DPORAS"),
                DetaljerLinje(bid.id, 7.jun21, 18.jun21, 100u, 100u, "DPORASFE"),
                DetaljerLinje(bid.id, 7.jul21, 20.jul21, 600u, 600u, "DPPEASFE1"),
                DetaljerLinje(bid.id, 7.jul21, 20.jul21, 300u, 300u, "DPPEAS"),
            ))
        )
        TestTopics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestTopics.utbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 1000u, 1000u)
                }
                assertEquals(expected, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR_FERIETILLEGG,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 100u, 100u)
                }
                assertEquals(expected, it)
            }
            .has(uid3.id.toString())
            .with(uid3.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.PERMITTERING_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 600u, 600u)
                }
                assertEquals(expected, it)
            }
            .has(uid4.id.toString())
            .with(uid4.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid4,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.PERMITTERING_ORDINÆR_FERIETILLEGG,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 300u, 300u)
                }
                assertEquals(expected, it)
            }
        TestTopics.oppdrag.assertThat()
            .has(originalKey, size = 1)
            .with(originalKey, index = 0) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(4, it.oppdrag110.oppdragsLinje150s.size)
                val førsteLinje = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(førsteLinje.refDelytelseId)
                assertEquals("NY", førsteLinje.kodeEndringLinje)
                assertEquals(bid.id, førsteLinje.henvisning)
                assertEquals("DPORAS", førsteLinje.kodeKlassifik)
                assertEquals(1000, førsteLinje.sats.toLong())
                assertEquals(1000, førsteLinje.vedtakssats157.vedtakssats.toLong())
                val andreLinje = it.oppdrag110.oppdragsLinje150s[1]
                assertEquals("NY", andreLinje.kodeEndringLinje)
                assertEquals(bid.id, andreLinje.henvisning)
                assertEquals("DPORASFE", andreLinje.kodeKlassifik)
                assertEquals(100, andreLinje.sats.toLong())
                assertEquals(100, andreLinje.vedtakssats157.vedtakssats.toLong())
                val tredjeLinje = it.oppdrag110.oppdragsLinje150s[2]
                assertEquals("NY", tredjeLinje.kodeEndringLinje)
                assertEquals(bid.id, tredjeLinje.henvisning)
                assertEquals("DPPEASFE1", tredjeLinje.kodeKlassifik)
                assertEquals(600, tredjeLinje.sats.toLong())
                assertEquals(600, tredjeLinje.vedtakssats157.vedtakssats.toLong())
                val fjerdeLinje = it.oppdrag110.oppdragsLinje150s[3]
                assertEquals("NY", fjerdeLinje.kodeEndringLinje)
                assertEquals(bid.id, fjerdeLinje.henvisning)
                assertEquals("DPPEAS", fjerdeLinje.kodeKlassifik)
                assertEquals(300, fjerdeLinje.sats.toLong())
                assertEquals(300, fjerdeLinje.vedtakssats157.vedtakssats.toLong())
            }
        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 4)
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 0) {
                assertEquals(it, setOf(uid1))
            }
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 1) {
                assertEquals(it, setOf(uid1, uid2))
            }
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 2) {
                assertEquals(it, setOf(uid1, uid2, uid3))
            }
            .with(SakKey(sid, Fagsystem.DAGPENGER), index = 3) {
                assertEquals(it, setOf(uid1, uid2, uid3, uid4))
            }
    }

    @Test
    fun `3 meldekort i 1 utbetalinger blir til 3 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val meldeperiode3 = "132462765"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid3 = dpUId(sid.id, meldeperiode3, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestTopics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 2377u,
                    utbetaltBeløp = 779u,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode3,
                    fom = LocalDate.of(2021, 8, 7),
                    tom = LocalDate.of(2021, 8, 20),
                    sats = 3133u,
                    utbetaltBeløp = 3000u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(listOf(
                DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DPORAS"),
                DetaljerLinje(bid.id, 7.jul21, 20.jul21, 2377u, 779u, "DPORAS"),
                DetaljerLinje(bid.id, 9.aug21, 20.aug21, 3133u, 3000u, "DPORAS"),
            ))
        )
        TestTopics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestTopics.utbetalinger.assertThat()
            .has(uid1.id.toString(), size = 1)
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
                }
                assertEquals(expected, it)
            }
            .has(uid2.id.toString())
            .with(uid2.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
                }
                assertEquals(expected, it)
            }
            .has(uid3.id.toString())
            .with(uid3.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 8, 9), LocalDate.of(2021, 8, 20), 3000u, 3133u)
                }
                assertEquals(expected, it)
            }
        TestTopics.oppdrag.assertThat()
            .has(originalKey, size = 1)
            .with(originalKey, index = 0) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(3, it.oppdrag110.oppdragsLinje150s.size)

                val linje1 = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(linje1.refDelytelseId)
                assertEquals("NY", linje1.kodeEndringLinje)
                assertEquals(bid.id, linje1.henvisning)
                assertEquals("DPORAS", linje1.kodeKlassifik)
                assertEquals(553, linje1.sats.toLong())
                assertEquals(1077, linje1.vedtakssats157.vedtakssats.toLong())

                val linje2 = it.oppdrag110.oppdragsLinje150s[1]
                assertNull(linje2.refDelytelseId)
                assertEquals("NY", linje2.kodeEndringLinje)
                assertEquals(bid.id, linje2.henvisning)
                assertEquals("DPORAS", linje2.kodeKlassifik)
                assertEquals(779, linje2.sats.toLong())
                assertEquals(2377, linje2.vedtakssats157.vedtakssats.toLong())

                val linje3 = it.oppdrag110.oppdragsLinje150s[2]
                assertNull(linje3.refDelytelseId)
                assertEquals("NY", linje3.kodeEndringLinje)
                assertEquals(bid.id, linje3.henvisning)
                assertEquals("DPORAS", linje3.kodeKlassifik)
                assertEquals(3000, linje3.sats.toLong())
                assertEquals(3133, linje3.vedtakssats157.vedtakssats.toLong())
            }
        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 3)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2), index = 1)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2, uid3), index = 2)
    }

    @Test
    fun `2 meldekort i 2 utbetalinger blir til 2 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR) // 16364e1c-7615-6b30-882b-d7d19ea96279
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR) // 6fa69f14-a3eb-1457-7859-b3676f59da9d

        TestTopics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                )
            }
        }
        TestTopics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 2377u,
                    utbetaltBeløp = 779u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(listOf(
                DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "DPORAS"),
                DetaljerLinje(bid.id, 7.jul21, 20.jul21, 2377u, 779u, "DPORAS"),
            ))
        )
        TestTopics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestTopics.utbetalinger.assertThat()
            .has(uid1.id.toString(), size = 1)
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
                }
                assertEquals(expected, it)
            }
            .has(uid2.id.toString())
            .with(uid2.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
                }
                assertEquals(expected, it)
            }
        TestTopics.oppdrag.assertThat()
            .has(originalKey, size = 1)
            .with(originalKey, index = 0) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(2, it.oppdrag110.oppdragsLinje150s.size)

                val linje1 = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(linje1.refDelytelseId)
                assertEquals("NY", linje1.kodeEndringLinje)
                assertEquals(bid.id, linje1.henvisning)
                assertEquals("DPORAS", linje1.kodeKlassifik)
                assertEquals(553, linje1.sats.toLong())
                assertEquals(1077, linje1.vedtakssats157.vedtakssats.toLong())

                val linje2 = it.oppdrag110.oppdragsLinje150s[1]
                assertNull(linje2.refDelytelseId)
                assertEquals("NY", linje2.kodeEndringLinje)
                assertEquals(bid.id, linje2.henvisning)
                assertEquals("DPORAS", linje2.kodeKlassifik)
                assertEquals(779, linje2.sats.toLong())
                assertEquals(2377, linje2.vedtakssats157.vedtakssats.toLong())
            }

        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2), index = 1)
    }

    @Test
    fun `nytt meldekort på eksisterende sak`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey1 = UUID.randomUUID().toString()
        val originalKey2 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid2 = dpUId(sid.id, meldeperiode2, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)

        TestTopics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey1,
                stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(3.jun, 14.jun, 100u)
            }
        }

        TestTopics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1)
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestTopics.dp.produce(originalKey2) {
            Dp.utbetaling(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = 3.jun,
                    tom = 14.jun,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                ) + Dp.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = 17.jun,
                    tom = 28.jun,
                    sats = 200u,
                    utbetaltBeløp = 200u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(listOf(
                DetaljerLinje(bid.id, 17.jun, 28.jun, 200u, 200u, "DPORAS"),
            ))
        )
        TestTopics.status.assertThat()
            .has(originalKey2)
            .has(originalKey2, mottatt)

        TestTopics.utbetalinger.assertThat()
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = originalKey2,
                    førsteUtbetalingPåSak = false,
                    utbetalingerPåSak = setOf(uid1),
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(17.jun, 28.jun, 200u, 200u)
                }
                assertEquals(expected, it)
            }

        TestTopics.oppdrag.assertThat()
            .has(originalKey2)
            .with(originalKey2) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("ENDR", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("DPORAS", a.kodeKlassifik)
                    assertEquals(200, a.sats.toLong())
                    assertEquals(200, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
            }

        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2), index = 1)
    }

    @Test
    fun `endre meldekort på eksisterende sak`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey1 = UUID.randomUUID().toString()
        val originalKey2 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val periodeId = PeriodeId()

        TestTopics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey1,
                stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(3.jun, 14.jun, 100u)
            }
        }

        TestTopics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1)
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestTopics.dp.produce(originalKey2) {
            Dp.utbetaling(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                Dp.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = 3.jun,
                    tom = 14.jun,
                    sats = 100u,
                    utbetaltBeløp = 80u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(listOf(
                DetaljerLinje(bid.id, 3.jun, 14.jun, 100u, 80u, "DPORAS"),
            ))
        )
        TestTopics.status.assertThat()
            .has(originalKey2)
            .has(originalKey2, mottatt)

        TestTopics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = originalKey2,
                    førsteUtbetalingPåSak = false,
                    utbetalingerPåSak = setOf(uid1),
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 14.jun, 80u, 100u)
                }
                assertEquals(expected, it)
            }

        TestTopics.oppdrag.assertThat()
            .has(originalKey2)
            .with(originalKey2) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)

                val førsteLinje = it.oppdrag110.oppdragsLinje150s[0]
                assertEquals(periodeId.toString(), førsteLinje.refDelytelseId)
                assertEquals("NY", førsteLinje.kodeEndringLinje)
                assertEquals(bid.id, førsteLinje.henvisning)
                assertEquals("DPORAS", førsteLinje.kodeKlassifik)
                assertEquals(80, førsteLinje.sats.toLong())
                assertEquals(100, førsteLinje.vedtakssats157.vedtakssats.toLong())
            }

        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1), index = 0)
    }

    @Test
    fun `opphør på meldekort`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey1 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val uid1 = dpUId(sid.id, meldeperiode1, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val periodeId = PeriodeId()

        TestTopics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey1,
                stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(2.jun, 13.jun, 100u)
            }
        }

        TestTopics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1)
        }

        TestTopics.dp.produce(originalKey1) {
            Dp.utbetaling(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                emptyList()
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            status = Status.MOTTATT, 
            detaljer = Detaljer(listOf(DetaljerLinje(bid.id, 2.jun, 13.jun, 100u, 0u, "DPORAS")))
        )

        TestTopics.status.assertThat()
            .has(originalKey1)
            .has(originalKey1, mottatt)

        TestTopics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.DELETE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = originalKey1,
                    utbetalingerPåSak = setOf(),
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = 14.jun.atStartOfDay(),
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.jun, 13.jun, 100u, 100u)
                }
                assertEquals(expected, it)
            }

        TestTopics.oppdrag.assertThat()
            .has(originalKey1)
            .with(originalKey1) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)

                val førsteLinje = it.oppdrag110.oppdragsLinje150s[0]
                assertEquals(TkodeStatusLinje.OPPH, førsteLinje.kodeStatusLinje)
                assertEquals(2.jun, førsteLinje.datoStatusFom.toLocalDate())
                assertEquals(periodeId.toString(), førsteLinje.refDelytelseId)
                assertEquals("NY", førsteLinje.kodeEndringLinje)
                assertEquals(bid.id, førsteLinje.henvisning)
                assertEquals("DPORAS", førsteLinje.kodeKlassifik)
                assertEquals(100, førsteLinje.sats.toLong())
                assertEquals(100, førsteLinje.vedtakssats157.vedtakssats.toLong())
            }

        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 2)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(), index = 1)

    }

    @Test
    fun `3 meldekort med ulike operasjoner`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val uid1 = dpUId(sid.id, "132460781", StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid2 = dpUId(sid.id, "232460781", StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val uid3 = dpUId(sid.id, "132462765", StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR)
        val pid1 = PeriodeId()
        val pid2 = PeriodeId()

        TestTopics.utbetalinger.produce(uid1.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey,
                stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                lastPeriodeId = pid1,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.sep.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(2.sep, 13.sep, 500u) // 1-14
            }
        }
        TestTopics.utbetalinger.produce(uid2.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid2,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey,
                stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                førsteUtbetalingPåSak = false,
                utbetalingerPåSak = setOf(uid1),
                lastPeriodeId = pid2,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.sep.atStartOfDay(),
                beslutterId = Navident("dagpenger"),
                saksbehandlerId = Navident("dagpenger"),
                fagsystem = Fagsystem.DAGPENGER,
            ) {
                periode(16.sep, 27.sep, 600u) // 15-28
            }
        }

        TestTopics.saker.produce(SakKey(sid, Fagsystem.DAGPENGER)) {
            setOf(uid1, uid2)
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestTopics.dp.produce(originalKey) {
            Dp.utbetaling(sid.id, bid.id) {
                Dp.meldekort("132460781", 2.sep, 13.sep, 600u) + 
                Dp.meldekort("132462765", 30.sep, 10.okt, 600u) // 29-12
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(listOf(
                DetaljerLinje(bid.id, 2.sep, 13.sep, 600u, 600u, "DPORAS"),
                DetaljerLinje(bid.id, 30.sep, 10.okt, 600u, 600u, "DPORAS"),
                DetaljerLinje(bid.id, 16.sep, 27.sep, 600u, 0u, "DPORAS"), // TODO: opphør skal settes til beløp 0?
            ))
        )
        TestTopics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestTopics.utbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE, // FIXME: burde vært en UPDATE
                    uid = uid1,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    utbetalingerPåSak = setOf(uid1, uid2), // TODO: skal uid3 også være med her?
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 600u)
                }
                assertEquals(expected, it)
            }
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.DELETE,
                    uid = uid2,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    utbetalingerPåSak = setOf(uid1),
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(16.sep, 27.sep, 600u)
                }
                assertEquals(expected, it)
            }
            .has(uid3.toString())
            .with(uid3.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid3,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.DAGPENGER,
                    lastPeriodeId = it.lastPeriodeId,
                    førsteUtbetalingPåSak = false,
                    utbetalingerPåSak = setOf(uid1, uid2), // FIXME: skal den ta med uid3? eller trenger den ikke ta med seg selv
                    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("dagpenger"),
                    saksbehandlerId = Navident("dagpenger"),
                    personident = Personident("12345678910")
                ) {
                    periode(30.sep, 10.okt, 600u)
                }
                assertEquals(expected, it)
            }
        TestTopics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("DP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("dagpenger", it.oppdrag110.saksbehId)
                assertEquals(3, it.oppdrag110.oppdragsLinje150s.size)

                val linje1 = it.oppdrag110.oppdragsLinje150s[0]
                assertEquals(pid1.toString(), linje1.refDelytelseId) // kjede på forrige
                assertEquals("NY", linje1.kodeEndringLinje)
                assertEquals(bid.id, linje1.henvisning)
                assertEquals("DPORAS", linje1.kodeKlassifik)
                assertEquals(600, linje1.sats.toLong())
                assertEquals(600, linje1.vedtakssats157.vedtakssats.toLong())

                val linje2 = it.oppdrag110.oppdragsLinje150s[1]
                assertNull(linje2.refDelytelseId)
                assertEquals("NY", linje2.kodeEndringLinje)
                assertEquals(bid.id, linje2.henvisning)
                assertEquals("DPORAS", linje2.kodeKlassifik)
                assertEquals(600, linje2.sats.toLong())
                assertEquals(600, linje2.vedtakssats157.vedtakssats.toLong())

                // FIXME: er opphøret alltid sist, eller er det tilfeldig hvor i lista den havner
                val linje3 = it.oppdrag110.oppdragsLinje150s[2]
                assertEquals(pid2.toString(), linje3.refDelytelseId) // kjede på forrige
                assertEquals(TkodeStatusLinje.OPPH, linje3.kodeStatusLinje)
                // assertEquals(2.jun, linje3.datoStatusFom.toLocalDate())
                assertEquals("NY", linje3.kodeEndringLinje)
                assertEquals(bid.id, linje3.henvisning)
                assertEquals("DPORAS", linje3.kodeKlassifik)
                assertEquals(600, linje3.sats.toLong())
                assertEquals(600, linje3.vedtakssats157.vedtakssats.toLong())
            }

        TestTopics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.DAGPENGER), size = 5)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2), index = 1)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2), index = 2)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid2, uid3), index = 3)
            .has(SakKey(sid, Fagsystem.DAGPENGER), setOf(uid1, uid3), index = 4)
    }

}
