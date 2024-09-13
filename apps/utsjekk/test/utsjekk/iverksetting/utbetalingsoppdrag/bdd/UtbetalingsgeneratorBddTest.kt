package utsjekk.iverksetting.utbetalingsoppdrag.bdd

import TestData.domain.andelData
import no.nav.utsjekk.kontrakter.felles.Satstype
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.AndelData
import utsjekk.iverksetting.AndelMedPeriodeId
import utsjekk.iverksetting.BehandlingId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val NOR_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

class UtbetalingsgeneratorBddTest {

    @AfterEach
    fun reset() = Bdd.reset()

    fun beregnUtbetalingsoppdragForTilkjenteYtelser(csvPath: String) {
        val csv = Csv.read(csvPath)
        Bdd.følgendeTilkjenteYtelser(csv, Input::from, Input::toAndel)
        Bdd.beregnUtbetalignsoppdrag()
        Bdd.forventFølgendeUtbetalingsoppdrag(csv, Expected::from, ForventetUtbetalingsoppdrag::from)
    }

    @Test
    fun `en periode får en lik periode`() {
        beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/en_periode_får_en_lik_periode.csv")
    }

    @Test
    fun `en periode får en ny periode`() {
        beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/en_periode_får_en_ny_periode.csv")
    }

    @Test
    fun `en periode får en ny periode engangssats`() {
        beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/en_periode_får_en_ny_periode_engangssats.csv")
    }

    @Test
    fun `en periode får en ny periode månedssats`() {
        beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/en_periode_får_en_ny_periode_månedssats.csv")
    }

    @Test
    fun `endring i periode`() {
        beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/endring_i_periode.csv")
    }

    @Nested
    inner class Forlenge {

        @Test
        fun `eksisterende periode forlenges i revurdering`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/forlenge/eksisterende_periode_forlenges_i_revurdering.csv")
        }

        @Test
        fun `periode med månedssats forlenges i slutten`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/forlenge/periode_med_månedssats_forlenges_i_slutten.csv")
        }

        @Test
        fun `periode med månedssats utvides i begge ender`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/forlenge/periode_med_månedssats_utvides_i_begge_ender.csv")
        }

        @Test
        fun `periode med månedssats utvides i starten`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/forlenge/periode_med_månedssats_utvides_i_starten.csv")
        }

        @Test
        fun `utbetaling med fler perioder der den andre perioden utvides i starten`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/forlenge/utbetaling_med_fler_perioder_der_den_andre_perioden_utvides_i_starten.csv")
        }

        @Test
        fun `utbetaling med fler perioder der den første perioden forlenges`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/forlenge/utbetaling_med_fler_perioder_der_den_første_perioden_forlenges.csv")
        }
    }

    @Nested
    inner class `korrigere beløp` {
        @Test
        fun `beløp endres tilbake i tid`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/korrigere_beløp/beløp_endres_tilbake_i_tid.csv")
        }

        @Test
        fun `endrer beløp for en hel periode med mnd-sats`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/korrigere_beløp/endrer_beløp_for_en_hel_periode_med_månedssats.csv")
        }

        @Test
        fun `endrer beløp for en gitt dato for utbet med mnd-sats`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/korrigere_beløp/endrer_beløp_fra_en_gitt_dato_for_utbetaling_med_månedssats.csv")
        }
    }

    @Nested
    inner class `0 beløp` {
        @Test
        fun `0 beløp beholdles og får en ny andel`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/nullbeløp/0_beløp_beholdes_og_får_en_ny_andel.csv")
        }

        @Test
        fun `endrer en tidligere periode til 0 utbetaling`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/nullbeløp/endrer_en_tidligere_periode_til_0_utbet.csv")
        }

        @Test
        fun `splitter en periode til 2 perioder der en får 0 beløp`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/nullbeløp/splitter_en_periode_til_2_perioder_der_en_får_0_beløp.csv")
        }
    }

    @Nested
    inner class Oppdrag {
        @Test
        fun `2 revurd som legger til en periode`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/oppdrag/2_revurd_som_legger_til_en_periode.csv")
        }

        @Test
        fun `endrer beløp fra 2 mar`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/oppdrag/endrer_beløp_fra_2_mar.csv")
        }

        @Test
        fun `endrer beløp fra start`() {
            data class Expected2(
                override val behandlingId: BehandlingId,
                val id: String,
                val periodeId: Long,
                val forrigePeriodeId: Long?,
            ) : WithBehandlingId(behandlingId)

            fun from(iter: Iterator<String>) = Expected2(
                behandlingId = BehandlingId(iter.next()),
                id = iter.next(),
                periodeId = iter.next().toLong(),
                forrigePeriodeId = iter.next().let { if (it.isBlank()) null else it.toLong() },
            )

            val csv = Csv.read("/csv/oppdrag/oppdrag/endrer_beløp_fra_start.csv")
            Bdd.følgendeTilkjenteYtelser(csv, Input::from, Input::toAndel)
            Bdd.beregnUtbetalignsoppdrag()
            Bdd.forventFølgendeUtbetalingsoppdrag(csv, Expected::from, ForventetUtbetalingsoppdrag::from)
            Bdd.forventFølgendeAndelerMedPeriodeId(csv, ::from) {
                AndelMedPeriodeId(
                    id = it.id,
                    periodeId = it.periodeId,
                    forrigePeriodeId = it.forrigePeriodeId
                )
            }
        }

        @Test
        fun `første periode blir avkortet og den andre er lik`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/oppdrag/første_periode_blir_avkortet_og_den_andre_er_lik.csv")
        }

        @Test
        fun `opphør alle perioder for å senere iverksette på ny`() {
            val csv = Csv.read("/csv/oppdrag/oppdrag/opphør_alle_perioder_for_å_senere_iverksette_på_ny.csv")
            Bdd.følgendeTilkjenteYtelser(csv, InputUtenAndeler::from, InputUtenAndeler::toAndel)
            Bdd.beregnUtbetalignsoppdrag()
            Bdd.forventFølgendeUtbetalingsoppdrag(csv, Expected::from, ForventetUtbetalingsoppdrag::from)
        }

        @Test
        fun `revurdering som legger til en periode`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/oppdrag/revurdering_som_legger_til_en_periode.csv")
        }

        @Test
        fun `revurdering uten endring av andeler`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/oppdrag/revurdering_uten_endring_av_andeler.csv")
        }

        @Test
        fun `vedtak med en periode`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/oppdrag/vedtak_med_en_periode.csv")
        }

        @Test
        fun `vedtak med to perioder`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/oppdrag/vedtak_med_to_perioder.csv")
        }
    }

    @Nested
    inner class Opphør {
        @Test
        fun `den første av 2 perioder`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/opphør/den_første_av_2_perioder.csv")
        }

        @Test
        fun `den første av 2 perioder der det er tid mellom periodene`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/opphør/den_første_av_2_perioder_der_det_er_tid_mellom_periodene.csv")
        }

        @Test
        fun `den siste av 2 perioder der det er tid mellom periodene`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/opphør/den_siste_av_2_perioder_der_det_er_tid_mellom_periodene.csv")
        }

        @Test
        fun `den siste av to perioder`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/opphør/den_siste_av_to_perioder.csv")
        }

        @Test
        fun `en av 2 perioder`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/opphør/en_av_2_perioder.csv")
        }

        @Test
        fun `en lang periode`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/opphør/en_lang_periode.csv")
        }

        @Test
        fun `en lang periode med mndsats`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/opphør/en_lang_periode_med_mndsats.csv")
        }

        @Test
        fun `en periode`() {
            val csv = Csv.read("/csv/oppdrag/opphør/en_periode.csv")
            Bdd.følgendeTilkjenteYtelser(csv, InputUtenAndeler::from, InputUtenAndeler::toAndel)
            Bdd.beregnUtbetalignsoppdrag()
            Bdd.forventFølgendeUtbetalingsoppdrag(csv, Expected::from, ForventetUtbetalingsoppdrag::from)
        }

        @Test
        fun `en periode med mndsats`() {
            val csv = Csv.read("/csv/oppdrag/opphør/en_periode_med_mndsats.csv")
            Bdd.følgendeTilkjenteYtelser(csv, InputUtenAndeler::from, InputUtenAndeler::toAndel)
            Bdd.beregnUtbetalignsoppdrag()
            Bdd.forventFølgendeUtbetalingsoppdrag(csv, Expected::from, ForventetUtbetalingsoppdrag::from)
        }

        @Test
        fun `en tidligere periode da vi kun har med den andre av 2 perioder`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/opphør/en_tidligere_periode_da_vi_kun_har_med_den_andre_av_2_perioder.csv")
        }

        @Test
        fun `første_mnd_av_en_lang_periode`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/opphør/første_mnd_av_en_lang_periode.csv")
        }

    }

    data class Input(
        override val behandlingId: BehandlingId,
        val fom: LocalDate,
        val tom: LocalDate,
        val beløp: Int,
        val satstype: Satstype,
    ) : WithBehandlingId(behandlingId) {
        companion object {
            fun from(iter: Iterator<String>) = Input(
                behandlingId = BehandlingId(iter.next()),
                fom = LocalDate.parse(iter.next(), NOR_DATE),
                tom = LocalDate.parse(iter.next(), NOR_DATE),
                beløp = iter.next().toInt(),
                satstype = Satstype.valueOf(iter.next()),
            )
        }

        fun toAndel(): AndelData = andelData(
            fom = this.fom,
            tom = this.tom,
            beløp = this.beløp,
            satstype = this.satstype,
        )
    }

    data class Expected(
        override val behandlingId: BehandlingId,
        val fom: LocalDate,
        val tom: LocalDate,
        val opphørsdato: LocalDate?,
        val beløp: Int,
        val førsteUtbetSak: Boolean,
        val erEndring: Boolean,
        val periodeId: Long,
        val forrigePeriodeId: Long?,
        val satstype: Satstype,
    ) : WithBehandlingId(behandlingId) {
        companion object {
            fun from(iter: Iterator<String>) = Expected(
                behandlingId = BehandlingId(iter.next()),
                fom = LocalDate.parse(iter.next(), NOR_DATE),
                tom = LocalDate.parse(iter.next(), NOR_DATE),
                opphørsdato = iter.next().let { if (it.isBlank()) null else LocalDate.parse(it, NOR_DATE) },
                beløp = iter.next().toInt(),
                førsteUtbetSak = iter.next().toBooleanStrict(),
                erEndring = iter.next().toBooleanStrict(),
                periodeId = iter.next().toLong(),
                forrigePeriodeId = iter.next().let { if (it.isBlank()) null else it.toLong() },
                satstype = Satstype.valueOf(iter.next()),
            )
        }
    }

    data class InputUtenAndeler(
        override val behandlingId: BehandlingId,
        val utenAndeler: Boolean,
        val fom: LocalDate?,
        val tom: LocalDate?,
        val beløp: Int?,
        val satstype: Satstype,
    ) : WithBehandlingId(behandlingId) {
        companion object {
            fun from(iter: Iterator<String>) = InputUtenAndeler(
                behandlingId = BehandlingId(iter.next()),
                utenAndeler = iter.next().toBoolean(),
                fom = iter.next().let { if (it.isBlank()) null else LocalDate.parse(it, NOR_DATE) },
                tom = iter.next().let { if (it.isBlank()) null else LocalDate.parse(it, NOR_DATE) },
                beløp = iter.next().let { if (it.isBlank()) null else it.toInt() },
                satstype = Satstype.valueOf(iter.next()),
            )
        }

        fun toAndel(): AndelData? = when (utenAndeler) {
            true -> null
            false -> andelData(
                fom = this.fom!!,
                tom = this.tom!!,
                beløp = this.beløp!!,
                satstype = this.satstype,
            )
        }
    }
}