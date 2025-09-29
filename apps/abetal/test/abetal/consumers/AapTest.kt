package abetal.consumers

import abetal.Aap
import abetal.SakKey
import abetal.TestRuntime
import abetal.aug21
import abetal.jul21
import abetal.jun
import abetal.jun21
import abetal.nextInt
import abetal.okt
import abetal.periode
import abetal.sep
import abetal.toLocalDate
import abetal.utbetaling
import java.time.LocalDate
import java.util.UUID
import models.Action
import models.BehandlingId
import models.Detaljer
import models.DetaljerLinje
import models.Fagsystem
import models.Navident
import models.PeriodeId
import models.Personident
import models.SakId
import models.Status
import models.StatusReply
import models.StønadTypeAAP
import models.aapUId
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class AapTest {

    @Test
    fun `1 meldekort i 1 utbetalinger blir til 1 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = aapUId(sid.id, meldeperiode, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid.id, bid.id) {
                Aap.meldekort(
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
            Detaljer(
                ytelse = Fagsystem.AAP,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "AAPOR"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("NY", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("AAPOR", a.kodeKlassifik)
                    assertEquals(553, a.sats.toLong())
                    assertEquals(1077, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())
            .with(uid.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 6, 7), LocalDate.of(2021, 6, 18), 553u, 1077u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 1)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid), index = 0)
    }

    @Test
    fun `2 meldekort i 1 utbetalinger blir til 2 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid.id, bid.id) {
                Aap.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                ) + Aap.meldekort(
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
            Detaljer(
                ytelse = Fagsystem.AAP,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "AAPOR"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, 2377u, 779u, "AAPOR"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey, size = 1)
            .with(originalKey, index = 0) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
                assertEquals(2, it.oppdrag110.oppdragsLinje150s.size)
                val førsteLinje = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(førsteLinje.refDelytelseId)
                assertEquals("NY", førsteLinje.kodeEndringLinje)
                assertEquals(bid.id, førsteLinje.henvisning)
                assertEquals("AAPOR", førsteLinje.kodeKlassifik)
                assertEquals(553, førsteLinje.sats.toLong())
                assertEquals(1077, førsteLinje.vedtakssats157.vedtakssats.toLong())
                val andreLinje = it.oppdrag110.oppdragsLinje150s[1]
                assertEquals("NY", andreLinje.kodeEndringLinje)
                assertEquals(bid.id, andreLinje.henvisning)
                assertEquals("AAPOR", andreLinje.kodeKlassifik)
                assertEquals(779, andreLinje.sats.toLong())
                assertEquals(2377, andreLinje.vedtakssats157.vedtakssats.toLong())
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
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
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 7, 7), LocalDate.of(2021, 7, 20), 779u, 2377u)
                }
                assertEquals(expected, it)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 2)
            .with(SakKey(sid, Fagsystem.AAP), index = 0) {
                assertEquals(it, setOf(uid1))
            }
            .with(SakKey(sid, Fagsystem.AAP), index = 1) {
                assertEquals(it, setOf(uid1, uid2))
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
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid3 = aapUId(sid.id, meldeperiode3, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid.id, bid.id) {
                Aap.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                ) + Aap.meldekort(
                    meldeperiode = meldeperiode2,
                    fom = LocalDate.of(2021, 7, 7),
                    tom = LocalDate.of(2021, 7, 20),
                    sats = 2377u,
                    utbetaltBeløp = 779u,
                ) + Aap.meldekort(
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
            Detaljer(
                ytelse = Fagsystem.AAP,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "AAPOR"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, 2377u, 779u, "AAPOR"),
                    DetaljerLinje(bid.id, 9.aug21, 20.aug21, 3133u, 3000u, "AAPOR"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
                assertEquals(3, it.oppdrag110.oppdragsLinje150s.size)

                val linje1 = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(linje1.refDelytelseId)
                assertEquals("NY", linje1.kodeEndringLinje)
                assertEquals(bid.id, linje1.henvisning)
                assertEquals("AAPOR", linje1.kodeKlassifik)
                assertEquals(553, linje1.sats.toLong())
                assertEquals(1077, linje1.vedtakssats157.vedtakssats.toLong())

                val linje2 = it.oppdrag110.oppdragsLinje150s[1]
                assertNull(linje2.refDelytelseId)
                assertEquals("NY", linje2.kodeEndringLinje)
                assertEquals(bid.id, linje2.henvisning)
                assertEquals("AAPOR", linje2.kodeKlassifik)
                assertEquals(779, linje2.sats.toLong())
                assertEquals(2377, linje2.vedtakssats157.vedtakssats.toLong())

                val linje3 = it.oppdrag110.oppdragsLinje150s[2]
                assertNull(linje3.refDelytelseId)
                assertEquals("NY", linje3.kodeEndringLinje)
                assertEquals(bid.id, linje3.henvisning)
                assertEquals("AAPOR", linje3.kodeKlassifik)
                assertEquals(3000, linje3.sats.toLong())
                assertEquals(3133, linje3.vedtakssats157.vedtakssats.toLong())
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.id.toString(), size = 1)
            .with(uid1.id.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid1,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid,
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
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
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
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
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(LocalDate.of(2021, 8, 9), LocalDate.of(2021, 8, 20), 3000u, 3133u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 3)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid2), index = 1)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid2, uid3), index = 2)
    }

    @Test
    fun `2 meldekort i 2 utbetalinger blir til 2 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid.id, bid.id) {
                Aap.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                )
            }
        }
        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid.id, bid.id) {
                Aap.meldekort(
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
            Detaljer(
                ytelse = Fagsystem.AAP,
                linjer = listOf(
                    DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "AAPOR"),
                    DetaljerLinje(bid.id, 7.jul21, 20.jul21, 2377u, 779u, "AAPOR"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
                assertEquals(2, it.oppdrag110.oppdragsLinje150s.size)
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.id.toString())
            .has(uid2.id.toString())

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 2)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1))
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid2), index = 1)
    }

    @Test
    fun `2 meldekort med 2 behandlinger for samme person blir til 2 utbetalinger med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid.id, bid1.id) {
                Aap.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                )
            }
        }
        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid.id, bid2.id) {
                Aap.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 20),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                ) +
                        Aap.meldekort(
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
            Detaljer(
                ytelse = Fagsystem.AAP,
                linjer = listOf(
                    DetaljerLinje(bid1.id, 7.jun21, 18.jun21, 1077u, 553u, "AAPOR"),
                    DetaljerLinje(bid2.id, 7.jul21, 20.jul21, 2377u, 779u, "AAPOR"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(2, it.oppdrag110.oppdragsLinje150s.size)

                val linje1 = it.oppdrag110.oppdragsLinje150s[0]
                assertEquals(bid1.id, linje1.henvisning)

                val linje2 = it.oppdrag110.oppdragsLinje150s[1]
                assertEquals(bid2.id, linje2.henvisning)
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.id.toString())
            .has(uid2.id.toString())

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 2)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1))
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid2), index = 1)
    }

    @Test
    fun `nytt meldekort på eksisterende sak`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey1 = UUID.randomUUID().toString()
        val originalKey2 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val meldeperiode2 = "232460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey1,
                stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("kelvin"),
                saksbehandlerId = Navident("kelvin"),
                fagsystem = Fagsystem.AAP,
            ) {
                periode(3.jun, 14.jun, 100u)
            }
        }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.AAP)) {
            setOf(uid1)
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.aap.produce(originalKey2) {
            Aap.utbetaling(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                Aap.meldekort(
                    meldeperiode = meldeperiode1,
                    fom = 3.jun,
                    tom = 14.jun,
                    sats = 100u,
                    utbetaltBeløp = 100u,
                ) + Aap.meldekort(
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
            Detaljer(
                ytelse = Fagsystem.AAP,
                linjer = listOf(
                    DetaljerLinje(bid.id, 17.jun, 28.jun, 200u, 200u, "AAPOR"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey2)
            .has(originalKey2, mottatt)

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey2)
            .with(originalKey2) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                it.oppdrag110.oppdragsLinje150s.windowed(2, 1) { (a, b) ->
                    assertEquals("ENDR", a.kodeEndringLinje)
                    assertEquals(bid.id, a.henvisning)
                    assertEquals("AAPOR", a.kodeKlassifik)
                    assertEquals(200, a.sats.toLong())
                    assertEquals(200, a.vedtakssats157.vedtakssats.toLong())
                    assertEquals(a.delytelseId, b.refDelytelseId)
                }
            }
            .get(originalKey2)

        TestRuntime.topics.oppdrag.produce(originalKey2) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid2.toString())
            .with(uid2.toString()) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid2,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = originalKey2,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(17.jun, 28.jun, 200u, 200u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 2)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid2), index = 1)
    }

    @Test
    fun `endre meldekort på eksisterende sak`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey1 = UUID.randomUUID().toString()
        val originalKey2 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey1,
                stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("kelvin"),
                saksbehandlerId = Navident("kelvin"),
                fagsystem = Fagsystem.AAP,
            ) {
                periode(3.jun, 14.jun, 100u)
            }
        }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.AAP)) {
            setOf(uid1)
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.aap.produce(originalKey2) {
            Aap.utbetaling(
                sakId = sid.id,
                behandlingId = bid.id,
                vedtakstidspunkt = 14.jun.atStartOfDay(),
            ) {
                Aap.meldekort(
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
            Detaljer(
                ytelse = Fagsystem.AAP,
                linjer = listOf(
                    DetaljerLinje(bid.id, 3.jun, 14.jun, 100u, 80u, "AAPOR"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey2)
            .has(originalKey2, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = originalKey2,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 14.jun, 80u, 100u)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey2)
            .with(originalKey2) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
                assertEquals(1, it.oppdrag110.oppdragsLinje150s.size)
                assertEquals(periodeId.toString(), it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)

                val førsteLinje = it.oppdrag110.oppdragsLinje150s[0]
                assertEquals("NY", førsteLinje.kodeEndringLinje)
                assertEquals("AAPOR", førsteLinje.kodeKlassifik)
                assertEquals(80, førsteLinje.sats.toLong())
                assertEquals(100, førsteLinje.vedtakssats157.vedtakssats.toLong())
            }
            .get(originalKey2)

        TestRuntime.topics.oppdrag.produce(originalKey2) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                val expected = utbetaling(
                    action = Action.UPDATE,
                    uid = uid1,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = originalKey2,
                    førsteUtbetalingPåSak = false,
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(3.jun, 14.jun, 80u, 100u)
                }
                assertEquals(expected, it)
            }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 2)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1), index = 0)
    }

    @Test
    fun `opphør på meldekort`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey1 = UUID.randomUUID().toString()
        val meldeperiode1 = "132460781"
        val uid1 = aapUId(sid.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val periodeId = PeriodeId()

        TestRuntime.topics.utbetalinger.produce("${uid1.id}") {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey1,
                stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                lastPeriodeId = periodeId,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.jun.atStartOfDay(),
                beslutterId = Navident("kelvin"),
                saksbehandlerId = Navident("kelvin"),
                fagsystem = Fagsystem.AAP,
            ) {
                periode(2.jun, 13.jun, 100u)
            }
        }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.AAP)) {
            setOf(uid1)
        }

        TestRuntime.topics.aap.produce(originalKey1) {
            Aap.utbetaling(
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
            detaljer = Detaljer(Fagsystem.AAP, listOf(DetaljerLinje(bid.id, 2.jun, 13.jun, 100u, 0u, "AAPOR")))
        )

        TestRuntime.topics.status.assertThat()
            .has(originalKey1)
            .has(originalKey1, mottatt)

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey1)
            .with(originalKey1) {
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                val førsteLinje = it.oppdrag110.oppdragsLinje150s[0]
                assertEquals(TkodeStatusLinje.OPPH, førsteLinje.kodeStatusLinje)
                assertEquals(2.jun, førsteLinje.datoStatusFom.toLocalDate())
                assertEquals("AAPOR", førsteLinje.kodeKlassifik)
            }
            .get(originalKey1)

        TestRuntime.topics.oppdrag.produce(originalKey1) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .with(uid1.toString()) {
                assertEquals(Action.DELETE, it.action)
                assertEquals(uid1, it.uid)
            }
        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 2)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1), index = 0)
            .has(SakKey(sid, Fagsystem.AAP), setOf(), index = 1)

    }

    @Test
    fun `3 meldekort med ulike operasjoner`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val uid1 = aapUId(sid.id, "132460781", StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid.id, "232460781", StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid3 = aapUId(sid.id, "132462765", StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val pid1 = PeriodeId()
        val pid2 = PeriodeId()

        TestRuntime.topics.utbetalinger.produce(uid1.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid1,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey,
                stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                lastPeriodeId = pid1,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.sep.atStartOfDay(),
                beslutterId = Navident("kelvin"),
                saksbehandlerId = Navident("kelvin"),
                fagsystem = Fagsystem.AAP,
            ) {
                periode(2.sep, 13.sep, 500u) // 1-14
            }
        }
        TestRuntime.topics.utbetalinger.produce(uid2.toString()) {
            utbetaling(
                action = Action.CREATE,
                uid = uid2,
                sakId = sid,
                behandlingId = bid,
                originalKey = originalKey,
                stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                førsteUtbetalingPåSak = false,
                lastPeriodeId = pid2,
                personident = Personident("12345678910"),
                vedtakstidspunkt = 14.sep.atStartOfDay(),
                beslutterId = Navident("kelvin"),
                saksbehandlerId = Navident("kelvin"),
                fagsystem = Fagsystem.AAP,
            ) {
                periode(16.sep, 27.sep, 600u) // 15-28
            }
        }

        TestRuntime.topics.saker.produce(SakKey(sid, Fagsystem.AAP)) {
            setOf(uid1, uid2)
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid.id, bid.id) {
                Aap.meldekort("132460781", 2.sep, 13.sep, 600u) +
                        Aap.meldekort("132462765", 30.sep, 10.okt, 600u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.AAP,
                linjer = listOf(
                    DetaljerLinje(bid.id, 2.sep, 13.sep, 600u, 600u, "AAPOR"),
                    DetaljerLinje(bid.id, 30.sep, 10.okt, 600u, 600u, "AAPOR"),
                    DetaljerLinje(bid.id, 16.sep, 27.sep, 600u, 0u, "AAPOR"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("ENDR", it.oppdrag110.kodeEndring)
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(3, it.oppdrag110.oppdragsLinje150s.size)

                val linje1 = it.oppdrag110.oppdragsLinje150s[0]
                assertEquals(pid1.toString(), linje1.refDelytelseId)

                val linje3 = it.oppdrag110.oppdragsLinje150s[2]
                assertEquals(pid2.toString(), linje3.refDelytelseId)
                assertEquals(TkodeStatusLinje.OPPH, linje3.kodeStatusLinje)
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.id.toString())
            .with(uid1.toString()) { assertEquals(Action.UPDATE, it.action) }
            .has(uid2.toString())
            .with(uid2.toString()) { assertEquals(Action.DELETE, it.action) }
            .has(uid3.toString())
            .with(uid3.toString()) { assertEquals(Action.CREATE, it.action) }

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 5)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid1, uid3), index = 4)
    }

    @Test
    @Disabled
    fun `en meldeperiode som endres 3 ganger samtidig skal feile`() {
        val sid = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val bid3 = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = aapUId(sid.id, meldeperiode, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid.id, bid1.id) {
                Aap.meldekort(meldeperiode, 2.sep, 13.sep, 300u, 300u)
            }
        }
        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid.id, bid2.id) {
                Aap.meldekort(meldeperiode, 2.sep, 13.sep, 300u, 300u)
                Aap.meldekort(meldeperiode, 16.sep, 27.sep, 300u, 300u)
            }
        }
        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid.id, bid3.id) {
                Aap.meldekort(meldeperiode, 2.sep, 13.sep, 300u, 300u)
                Aap.meldekort(meldeperiode, 16.sep, 27.sep, 300u, 300u)
                Aap.meldekort(meldeperiode, 30.sep, 10.okt, 300u, 300u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        val mottatt = StatusReply(
            Status.MOTTATT,
            Detaljer(
                ytelse = Fagsystem.AAP,
                linjer = listOf(
                    DetaljerLinje(bid1.id, 2.sep, 13.sep, 300u, 300u, "AAPOR"),
                    DetaljerLinje(bid2.id, 16.sep, 27.sep, 300u, 300u, "AAPOR"),
                    DetaljerLinje(bid3.id, 30.sep, 10.okt, 300u, 300u, "AAPOR"),
                )
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        TestRuntime.topics.pendingUtbetalinger.assertThat()
            .has(uid.toString(), 1)
            .with(uid.toString(), index = 0) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid3,
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 300u, 300u) +
                    periode(16.sep, 27.sep, 300u, 300u) +
                    periode(30.sep, 10.okt, 300u, 300u)
                }
                assertEquals(expected, it)
            }

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("1", it.oppdrag110.kodeAksjon)
                assertEquals("NY", it.oppdrag110.kodeEndring)
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals(sid.id, it.oppdrag110.fagsystemId)
                assertEquals("MND", it.oppdrag110.utbetFrekvens)
                assertEquals("12345678910", it.oppdrag110.oppdragGjelderId)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
                assertEquals(3, it.oppdrag110.oppdragsLinje150s.size)
                assertNull(it.oppdrag110.oppdragsLinje150s[0].refDelytelseId)
                val l1 = it.oppdrag110.oppdragsLinje150s[0]
                assertNull(l1.refDelytelseId)
                assertEquals("NY", l1.kodeEndringLinje)
                assertEquals(bid1.id, l1.henvisning)
                assertEquals("AAPOR", l1.kodeKlassifik)
                assertEquals(300, l1.sats.toLong())
                assertEquals(300, l1.vedtakssats157.vedtakssats.toLong())
                val l2 = it.oppdrag110.oppdragsLinje150s[1]
                assertEquals(l1.delytelseId, l2.refDelytelseId)
                assertEquals("NY", l2.kodeEndringLinje)
                assertEquals(bid2.id, l2.henvisning)
                assertEquals("AAPOR", l2.kodeKlassifik)
                assertEquals(300, l2.sats.toLong())
                assertEquals(300, l2.vedtakssats157.vedtakssats.toLong())
                val l3 = it.oppdrag110.oppdragsLinje150s[2]
                assertEquals(l2.delytelseId, l3.refDelytelseId)
                assertEquals("NY", l3.kodeEndringLinje)
                assertEquals(bid3.id, l3.henvisning)
                assertEquals("AAPOR", l3.kodeKlassifik)
                assertEquals(300, l3.sats.toLong())
                assertEquals(300, l3.vedtakssats157.vedtakssats.toLong())
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString(), size = 1)
            .with(uid.toString(), index = 0) {
                val expected = utbetaling(
                    action = Action.CREATE,
                    uid = uid,
                    originalKey = originalKey,
                    sakId = sid,
                    behandlingId = bid3,
                    fagsystem = Fagsystem.AAP,
                    lastPeriodeId = it.lastPeriodeId,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    vedtakstidspunkt = it.vedtakstidspunkt,
                    beslutterId = Navident("kelvin"),
                    saksbehandlerId = Navident("kelvin"),
                    personident = Personident("12345678910")
                ) {
                    periode(2.sep, 13.sep, 300u, 300u) +
                    periode(16.sep, 27.sep, 300u, 300u) +
                    periode(30.sep, 10.okt, 300u, 300u)
                }
                assertEquals(expected, it)
            }


        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 1)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid), index = 0)
    }

    @Test
    @Disabled
    fun `3 saker blir til 3 utbetalinger med 3 oppdrag`() {
        val sid1 = SakId("$nextInt")
        val sid2 = SakId("$nextInt")
        val sid3 = SakId("$nextInt")
        val bid1 = BehandlingId("$nextInt")
        val bid2 = BehandlingId("$nextInt")
        val bid3 = BehandlingId("$nextInt")
        val originalKey = "12345678910"
        val meldeperiode1 = "100000000"
        val meldeperiode2 = "200000000"
        val meldeperiode3 = "300000000"
        val uid1 = aapUId(sid1.id, meldeperiode1, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid2 = aapUId(sid2.id, meldeperiode2, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)
        val uid3 = aapUId(sid3.id, meldeperiode3, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid1.id, bid1.id) {
                Aap.meldekort(meldeperiode1, 2.sep, 13.sep, 300u, 300u)
            }
        }
        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid2.id, bid2.id) {
                Aap.meldekort(meldeperiode2, 16.sep, 27.sep, 300u, 300u)
            }
        }
        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid3.id, bid3.id) {
                Aap.meldekort(meldeperiode3, 30.sep, 10.okt, 300u, 300u)
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.status.assertThat().has(originalKey, 3)
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val assertOppdrag = TestRuntime.topics.oppdrag.assertThat()
        assertOppdrag.has(originalKey, size = 3)
            .with(originalKey, index = 0) { assertEquals(sid1.id, it.oppdrag110.fagsystemId) }
            .with(originalKey, index = 1) { assertEquals(sid2.id, it.oppdrag110.fagsystemId) }
            .with(originalKey, index = 2) { assertEquals(sid3.id, it.oppdrag110.fagsystemId) }

        val oppdrag1 = assertOppdrag.get(originalKey, index = 0)
        val oppdrag2 = assertOppdrag.get(originalKey, index = 1)
        val oppdrag3 = assertOppdrag.get(originalKey, index = 2)

        TestRuntime.topics.oppdrag.produce(originalKey) {
            oppdrag1.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
        }
        TestRuntime.topics.oppdrag.produce(originalKey) {
            oppdrag2.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
        }
        TestRuntime.topics.oppdrag.produce(originalKey) {
            oppdrag3.apply { mmel = Mmel().apply { alvorlighetsgrad = "00" } }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid1.toString())
            .has(uid2.toString())
            .has(uid3.toString())

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid1, Fagsystem.AAP))
            .has(SakKey(sid2, Fagsystem.AAP))
            .has(SakKey(sid3, Fagsystem.AAP))
    }

    @Test
    fun `simuler 1 meldekort i 1 utbetalinger`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()

        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid.id, bid.id, dryrun = true) {
                Aap.meldekort(
                    meldeperiode = "132460781",
                    fom = LocalDate.of(2021, 6, 7),
                    tom = LocalDate.of(2021, 6, 18),
                    sats = 1077u,
                    utbetaltBeløp = 553u,
                )
            }
        }

        TestRuntime.kafka.advanceWallClockTime(1001.milliseconds)

        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.utbetalinger.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.saker.assertThat().isEmpty()
        TestRuntime.topics.simulering.assertThat()
            .hasTotal(1)
            .has(originalKey)
            .with(originalKey) {
                assertEquals("AAP", it.request.oppdrag.kodeFagomraade)
                assertEquals(sid.id, it.request.oppdrag.fagsystemId)
                assertEquals("kelvin", it.request.oppdrag.saksbehId)
                val l1 = it.request.oppdrag.oppdragslinjes[0]
                assertEquals("AAPOR", l1.kodeKlassifik)
                assertEquals(553, l1.sats.toLong())
            }
    }

    @Test
    fun `test 1 meldekort i 1 utbetalinger blir til 1 utbetaling med 1 oppdrag`() {
        val sid = SakId("$nextInt")
        val bid = BehandlingId("$nextInt")
        val originalKey = UUID.randomUUID().toString()
        val meldeperiode = "132460781"
        val uid = aapUId(sid.id, meldeperiode, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING)

        // TODO: Skulle dette vært aapUtbetalinger topic, tilsvarende som i DP testen?
        TestRuntime.topics.aap.produce(originalKey) {
            Aap.utbetaling(sid.id, bid.id) {
                Aap.meldekort(
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
            Detaljer(
                ytelse = Fagsystem.AAP,
                linjer = listOf(DetaljerLinje(bid.id, 7.jun21, 18.jun21, 1077u, 553u, "AAPOR"))
            )
        )
        TestRuntime.topics.status.assertThat()
            .has(originalKey)
            .has(originalKey, mottatt)

        TestRuntime.topics.utbetalinger.assertThat().isEmpty()

        val oppdrag = TestRuntime.topics.oppdrag.assertThat()
            .has(originalKey)
            .with(originalKey) {
                assertEquals("AAP", it.oppdrag110.kodeFagomraade)
                assertEquals("kelvin", it.oppdrag110.saksbehId)
            }
            .get(originalKey)

        TestRuntime.topics.oppdrag.produce(originalKey) {
            oppdrag.apply {
                mmel = Mmel().apply { alvorlighetsgrad = "00" }
            }
        }

        TestRuntime.topics.utbetalinger.assertThat()
            .has(uid.toString())

        TestRuntime.topics.saker.assertThat()
            .has(SakKey(sid, Fagsystem.AAP), size = 1)
            .has(SakKey(sid, Fagsystem.AAP), setOf(uid), index = 0)
    }
}
