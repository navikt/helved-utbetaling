package simulering

import java.time.LocalDate

/**
 * Enhet kan være enten [tknr] eller [orgnr]+[avd]
 * 4 eller opptil 13 tegn
 */
typealias EnhetNr = String
typealias FnrOrgnr = String // 9-11 tegn
typealias String1 = String // 0-1
typealias LinjeId = Int
typealias NavIdent = String // 8
typealias Klasse = String // 0-20

data class SimuleringResponse(
    val simulerBeregningResponse: SimulerBeregningResponse,
) {
    data class SimulerBeregningResponse(
        val response: Response,
    ) {
        data class Response(
            val simulering: Beregning,
            val infomelding: Infomelding?
        ) {
            // entiteten sin referanse-id 311
            data class Beregning(
                val gjelderId: FnrOrgnr,
                val gjelderNavn: String,
                /** Ved simuleringsbereging gjelder dette datoen beregningen vil kjæres på. */
                val datoBeregnet: LocalDate,
                val kodeFaggruppe: String,
                val belop: Double,
                val beregningsPeriode: List<Periode>
            ) {

                fun intoDto(): Simulering =
                    Simulering(
                        gjelderId = gjelderId,
                        gjelderNavn = gjelderNavn,
                        datoBeregnet = datoBeregnet,
                        totalBelop = belop.toInt(), // todo: riktig?
                        perioder = beregningsPeriode.map(Periode::intoDto),
                    )

                // entiteten sin referanse-id 312
                data class Periode(
                    val periodeFom: LocalDate,
                    val periodeTom: LocalDate,
                    val beregningStoppnivaa: List<Stoppnivå>
                ) {
                    fun intoDto(): SimulertPeriode =
                        SimulertPeriode(
                            fom = periodeFom,
                            tom = periodeTom,
                            utbetalinger = beregningStoppnivaa.map(Stoppnivå::intoDto)
                        )

                    // entiteten sin referanse-id 313
                    data class Stoppnivå(
                        val kodeFagomraade: String,
                        val stoppNivaaId: LinjeId,
                        val behandlendeEnhet: EnhetNr,
                        val oppdragsId: Long,
                        val fagsystemId: String,
                        val kid: String,
                        val utbetalesTilId: FnrOrgnr,
                        val utbetalesTilNavn: String,
                        val bilagsType: String,
                        val forfall: LocalDate,
                        val feilkonto: Boolean,
                        val beregningStoppnivaaDetaljer: List<Detalj>,
                    ) {
                        fun intoDto(): Utbetaling =
                            Utbetaling(
                                fagSystemId = fagsystemId,
                                utbetalesTilId = utbetalesTilId.removePrefix("00"),
                                utbetalesTilNavn = utbetalesTilNavn,
                                forfall = forfall,
                                feilkonto = feilkonto,
                                detaljer = beregningStoppnivaaDetaljer.map(Detalj::intoDto),
                            )

                        // entiteten sin referanse-id 314
                        data class Detalj(
                            val faktiskFom: LocalDate,
                            val faktiskTom: LocalDate,
                            val kontoStreng: String,
                            val behandlingskode: String1,
                            val belop: Double,
                            val trekkVedtakId: Long,
                            val stonadId: String,
                            val korrigering: String1,
                            val tilbakeforing: Boolean,
                            val linjeId: LinjeId,
                            val sats: Double,
                            val typeSats: SatsType,
                            val antallSats: Double,
                            val saksbehId: NavIdent,
                            val uforeGrad: Int, // 0-100
                            val kravhaverId: FnrOrgnr,
                            val delytelseId: String,
                            val bostedsenhet: EnhetNr,
                            val skykldnerId: FnrOrgnr,
                            val klassekode: Klasse,
                            val klasseKodeBeskrivelse: String,
                            val typeKlasse: Klasse,
                            val typeKlasseBeskrivelse: String,
                            val refunderesOrgNr: FnrOrgnr,

                            ) {
                            fun intoDto(): Detaljer =
                                Detaljer(
                                    faktiskFom = faktiskFom,
                                    faktiskTom = faktiskTom,
                                    konto = kontoStreng,
                                    belop = belop.toInt(), // todo: riktig?
                                    tilbakeforing = tilbakeforing,
                                    sats = sats,
                                    typeSats = typeSats.name,
                                    antallSats = antallSats.toInt(), // todo: riktig?
                                    uforegrad = uforeGrad,
                                    utbetalingsType = typeKlasse,
                                    klassekode = klassekode,
                                    klassekodeBeskrivelse = klasseKodeBeskrivelse,
                                    refunderesOrgNr = refunderesOrgNr.removePrefix("00"),
                                )

                            enum class SatsType {
                                DAG,
                                UKE,
                                `14DB`,
                                MND,
                                AAR,
                                ENG,
                                AKTO
                            }
                        }

                    }
                }
            }

            data class Infomelding(val beskrMelding: String)
        }

        data class Fault(
            val faultcode: String,
            val faultstring: String,
        )
    }
}
