package utsjekk.iverksetting.utbetalingsoppdrag.bdd

import TestData.domain.andelData
import no.nav.utsjekk.kontrakter.felles.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.*
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

    @Nested
    inner class Periode {
        @Test
        fun `får lik periode`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/periode/lik_periode.csv")
        }

        @Test
        fun `får ny periode`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/periode/ny_periode.csv")
        }

        @Test
        fun `får ny periode engangssats`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/periode/ny_periode_engangssats.csv")
        }

        @Test
        fun `får ny periode månedssats`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/periode/ny_periode_månedssats.csv")
        }

        @Test
        fun `får endring i periode`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/periode/endring_i_periode.csv")
        }
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
            val csv = Csv.read("/csv/oppdrag/oppdrag/endrer_beløp_fra_start.csv")
            Bdd.følgendeTilkjenteYtelser(csv, Input::from, Input::toAndel)
            Bdd.beregnUtbetalignsoppdrag()
            Bdd.forventFølgendeUtbetalingsoppdrag(csv, Expected::from, ForventetUtbetalingsoppdrag::from)
            Bdd.forventFølgendeAndelerMedPeriodeId(csv, ExpectedAndeler::from, ExpectedAndeler::toAndelMedPeriodeId)
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
        fun periode() {
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

        @Test
        fun `2 opphør etter hverandre på ulike perioder`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/opphør/2_opphør_etter_hverandre_på_ulike_perioder.csv")
        }

        @Test
        fun `avkorte en periode som opphøres`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/opphør/avkorte_en_periode_som_opphøres.csv")
        }

        @Test
        fun `forkorter en enkelt periode fler ganger`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/opphør/forkorter_en_enkelt_periode_fler_ganger.csv")
        }

        @Test
        fun `iverksett på nytt etter opphør`() {
            val csv = Csv.read("/csv/oppdrag/opphør/iverksett_på_nytt_etter_opphør.csv")
            Bdd.følgendeTilkjenteYtelser(csv, InputUtenAndeler::from, InputUtenAndeler::toAndel)
            Bdd.beregnUtbetalignsoppdrag()
            Bdd.forventFølgendeUtbetalingsoppdrag(csv, Expected::from, ForventetUtbetalingsoppdrag::from)
        }

        @Test
        fun `mellom 2 andeler`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/opphør/mellom_2_andeler.csv")
        }

    }

    @Nested
    inner class Revurdering {
        @Test
        fun `frem i tid`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/revurdering/frem_i_tid.csv")
        }

        @Test
        fun `opphør før ny periode`() {
            val csv = Csv.read("/csv/oppdrag/revurdering/opphør_før_ny_periode.csv")
            Bdd.følgendeTilkjenteYtelser(csv, InputUtenAndeler::from, InputUtenAndeler::toAndel)
            Bdd.beregnUtbetalignsoppdrag()
            Bdd.forventFølgendeUtbetalingsoppdrag(csv, Expected::from, ForventetUtbetalingsoppdrag::from)
        }

        @Test
        fun `uten andeler`() {
            val csv = Csv.read("/csv/oppdrag/revurdering/uten_andeler.csv")
            Bdd.følgendeTilkjenteYtelser(csv, InputUtenAndeler::from, InputUtenAndeler::toAndel)
            Bdd.beregnUtbetalignsoppdrag()
            Bdd.forventFølgendeUtbetalingsoppdrag(csv, Expected::from, ForventetUtbetalingsoppdrag::from)
        }

        @Test
        fun `sletter andre periode revurderer på nytt`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/revurdering/sletter_andre_periode_revurderer_på_nytt.csv")
        }
    }

    @Nested
    inner class Stønadstyper {
        @Test
        fun `søker med ordinær arbeidssøker og permittering`() {
            val csv = Csv.read("/csv/oppdrag/stønadstyper/ordinær_arbd_søker.csv")
            Bdd.følgendeTilkjenteYtelser(csv, InputMedYtelse::from, InputMedYtelse::toAndel)
            Bdd.beregnUtbetalignsoppdrag()
            Bdd.forventFølgendeUtbetalingsoppdrag(csv, ExpectedMedYtelse::from, ForventetUtbetalingsoppdrag::fromExpectedMedYtelse)
        }
        @Test
        fun `revurdering endrer beløp på permittering`() {
            val csv = Csv.read("/csv/oppdrag/stønadstyper/revurd_endrer_beløp_på_permit.csv")
            Bdd.følgendeTilkjenteYtelser(csv, InputMedYtelse::from, InputMedYtelse::toAndel)
            Bdd.beregnUtbetalignsoppdrag()
            Bdd.forventFølgendeUtbetalingsoppdrag(csv, ExpectedMedYtelse::from, ForventetUtbetalingsoppdrag::fromExpectedMedYtelse)
        }
        @Test
        fun `søker har fler stønadstyper som alle blir egne kjeder`() {
            val csv = Csv.read("/csv/oppdrag/stønadstyper/fler_stønadstyper.csv")
            Bdd.følgendeTilkjenteYtelser(csv, InputMedYtelse::from, InputMedYtelse::toAndel)
            Bdd.beregnUtbetalignsoppdrag()
            Bdd.forventFølgendeUtbetalingsoppdrag(csv, ExpectedMedYtelse::from, ForventetUtbetalingsoppdrag::fromExpectedMedYtelse)
        }
    }

    @Nested
    inner class `to perioder` {

        @Test
        fun `endring på den andre`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/to_perioder/endring_i_andre_perioden.csv")
        }
        @Test
        fun `endring på den andre2`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/to_perioder/endring_i_andre_perioden2.csv")
        }

        @Test
        fun `endring på den første`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/to_perioder/endring_i_første_perioden.csv")
        }
        
        @Test
        fun `får ny periode før første`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/to_perioder/ny_periode_før_første_periode.csv")
        }
        
        @Test
        fun `får ny periode og endring i andre`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/to_perioder/ny_periode_og_endring_i_andre_perioden.csv")
        }
    }

    @Nested
    inner class `tre perioder`{
        @Test
        fun `endring i første periode`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/tre_perioder/endring_i_første_periode.csv")
        }
        @Test
        fun `endring i andre periode`() {
            beregnUtbetalingsoppdragForTilkjenteYtelser("/csv/oppdrag/tre_perioder/endring_i_andre_periode.csv")
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

    data class ExpectedAndeler(
        override val behandlingId: BehandlingId,
        val id: String,
        val periodeId: Long,
        val forrigePeriodeId: Long?,
    ) : WithBehandlingId(behandlingId) {
        companion object {
            fun from(iter: Iterator<String>) = ExpectedAndeler(
                behandlingId = BehandlingId(iter.next()),
                id = iter.next(),
                periodeId = iter.next().toLong(),
                forrigePeriodeId = iter.next().let { if (it.isBlank()) null else it.toLong() },
            )
        }

        fun toAndelMedPeriodeId() = AndelMedPeriodeId(
            id = this.id,
            periodeId = this.periodeId,
            forrigePeriodeId = this.forrigePeriodeId
        )
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

    data class InputMedYtelse(
        override val behandlingId: BehandlingId,
        val fom: LocalDate,
        val tom: LocalDate,
        val beløp: Int,
        val ytelse: StønadType,
        val satstype: Satstype,
    ) : WithBehandlingId(behandlingId) {
        companion object {
            fun from(iter: Iterator<String>) = InputMedYtelse(
                behandlingId = BehandlingId(iter.next()),
                fom = LocalDate.parse(iter.next(), NOR_DATE),
                tom = LocalDate.parse(iter.next(), NOR_DATE),
                beløp = iter.next().toInt(),
                ytelse = iter.next().toStønadType(),
                satstype = Satstype.valueOf(iter.next()),
            )

            private fun String.toStønadType(): StønadType =
                runCatching {
                    StønadTypeDagpenger.valueOf(this)
                }.getOrNull() ?: runCatching {
                    StønadTypeTilleggsstønader.valueOf(this)
                }.getOrNull() ?: run {
                    StønadTypeTiltakspenger.valueOf(this)
                }
        }

        fun toAndel(): AndelData = andelData(
            fom = this.fom,
            tom = this.tom,
            beløp = this.beløp,
            stønadsdata = createStønadsdata(),
            satstype = this.satstype,
        )

        private fun createStønadsdata(): Stønadsdata =
            when (ytelse) {
                is StønadTypeDagpenger -> StønadsdataDagpenger(ytelse)
                is StønadTypeTilleggsstønader -> StønadsdataTilleggsstønader(ytelse)
                is StønadTypeTiltakspenger -> StønadsdataTiltakspenger(ytelse, brukersNavKontor = BrukersNavKontor("1234"))
            }
    }

    data class ExpectedMedYtelse(
        override val behandlingId: BehandlingId,
        val fom: LocalDate,
        val tom: LocalDate,
        val opphørsdato: LocalDate?,
        val beløp: Int,
        val ytelse: StønadType,
        val førsteUtbetSak: Boolean,
        val erEndring: Boolean,
        val periodeId: Long,
        val forrigePeriodeId: Long?,
        val satstype: Satstype,
    ) : WithBehandlingId(behandlingId) {
        companion object {
            fun from(iter: Iterator<String>) = ExpectedMedYtelse(
                behandlingId = BehandlingId(iter.next()),
                fom = LocalDate.parse(iter.next(), NOR_DATE),
                tom = LocalDate.parse(iter.next(), NOR_DATE),
                opphørsdato = iter.next().let { if (it.isBlank()) null else LocalDate.parse(it, NOR_DATE) },
                beløp = iter.next().toInt(),
                ytelse = iter.next().toStønadType(),
                førsteUtbetSak = iter.next().toBooleanStrict(),
                erEndring = iter.next().toBooleanStrict(),
                periodeId = iter.next().toLong(),
                forrigePeriodeId = iter.next().let { if (it.isBlank()) null else it.toLong() },
                satstype = Satstype.valueOf(iter.next()),
            )

            private fun String.toStønadType(): StønadType =
                runCatching {
                    StønadTypeDagpenger.valueOf(this)
                }.getOrNull() ?: runCatching {
                    StønadTypeTilleggsstønader.valueOf(this)
                }.getOrNull() ?: run {
                    StønadTypeTiltakspenger.valueOf(this)
                }
        }
    }
}
