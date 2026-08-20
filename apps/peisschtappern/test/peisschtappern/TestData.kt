package peisschtappern

import models.Fagsystem
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TestData {
    fun simuleringXml(sakId: String, fagsystem: String) = """
        <ns3:simulerBeregningRequest xmlns:ns2="http://nav.no/system/os/entiteter/oppdragSkjema" xmlns:ns3="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
            <request>
                <oppdrag>
                    <kodeEndring>NY</kodeEndring>
                    <kodeFagomraade>$fagsystem</kodeFagomraade>
                    <fagsystemId>$sakId</fagsystemId>
                    <utbetFrekvens>MND</utbetFrekvens>
                    <oppdragGjelderId>16499045070</oppdragGjelderId>
                    <datoOppdragGjelderFom>2000-01-01</datoOppdragGjelderFom>
                    <saksbehId>ts</saksbehId>
                    <ns2:enhet>
                        <typeEnhet>BOS</typeEnhet>
                        <enhet>8020</enhet>
                        <datoEnhetFom>1970-01-01</datoEnhetFom>
                    </ns2:enhet>
                    <oppdragslinje>
                        <kodeEndringLinje>NY</kodeEndringLinje>
                        <delytelseId>/wLcR7N0Txm5mzUBJ6elQQ==</delytelseId>
                        <kodeKlassifik>TSDRASISP3-OP</kodeKlassifik>
                        <datoKlassifikFom>2026-01-05</datoKlassifikFom>
                        <datoVedtakFom>2026-01-05</datoVedtakFom>
                        <datoVedtakTom>2026-01-05</datoVedtakTom>
                        <sats>2080</sats>
                        <fradragTillegg>T</fradragTillegg>
                        <typeSats>DAG</typeSats>
                        <brukKjoreplan>N</brukKjoreplan>
                        <saksbehId>ts</saksbehId>
                        <utbetalesTilId>16499045070</utbetalesTilId>
                        <ns2:attestant>
                            <attestantId>ts</attestantId>
                        </ns2:attestant>
                    </oppdragslinje>
                </oppdrag>
            </request>
        </ns3:simulerBeregningRequest>

    """.trimIndent()

    fun oppdragXml(sakId: String = "202503271001", alvorlighetsgrad: String? = null, fagsystem: String? = "TILTPENG") =
        """
<ns2:oppdrag xmlns:ns2="http://www.trygdeetaten.no/skjema/oppdrag">
  ${alvorlighetsgrad?.let { "<mmel><alvorlighetsgrad>$it</alvorlighetsgrad></mmel>" } ?: ""}
  <oppdrag-110>
    <kodeAksjon>1</kodeAksjon>
    <kodeEndring>ENDR</kodeEndring>
    <kodeFagomraade>$fagsystem</kodeFagomraade>
    <fagsystemId>$sakId</fagsystemId>
    <utbetFrekvens>MND</utbetFrekvens>
    <oppdragGjelderId>14439535912</oppdragGjelderId>
    <datoOppdragGjelderFom>2000-01-01+01:00</datoOppdragGjelderFom>
    <saksbehId>Z990123</saksbehId>
    <avstemming-115>
      <kodeKomponent>TILTPENG</kodeKomponent>
      <nokkelAvstemming>2025-04-10-11.00.00.000000</nokkelAvstemming>
      <tidspktMelding>2025-04-10-11.00.00.000000</tidspktMelding>
    </avstemming-115>
    <oppdrags-enhet-120>
      <typeEnhet>BOS</typeEnhet>
      <enhet>0321</enhet>
      <datoEnhetFom>1970-01-01+01:00</datoEnhetFom>
    </oppdrags-enhet-120>
    <oppdrags-enhet-120>
      <typeEnhet>BEH</typeEnhet>
      <enhet>8020</enhet>
      <datoEnhetFom>1970-01-01+01:00</datoEnhetFom>
    </oppdrags-enhet-120>
    <oppdrags-linje-150>
      <kodeEndringLinje>NY</kodeEndringLinje>
      <vedtakId>2025-04-10</vedtakId>
      <delytelseId>202503271001#16</delytelseId>
      <kodeKlassifik>TPTPAFT</kodeKlassifik>
      <datoVedtakFom>2025-01-27+01:00</datoVedtakFom>
      <datoVedtakTom>2025-01-29+01:00</datoVedtakTom>
      <sats>298</sats>
      <fradragTillegg>T</fradragTillegg>
      <typeSats>DAG7</typeSats>
      <brukKjoreplan>N</brukKjoreplan>
      <saksbehId>Z990123</saksbehId>
      <utbetalesTilId>14439535912</utbetalesTilId>
      <henvisning>22SK08N2GB3GQ7E</henvisning>
      <refFagsystemId>202503271001</refFagsystemId>
      <refDelytelseId>202503271001#12</refDelytelseId>
      <attestant-180>
        <attestantId>Z994127</attestantId>
      </attestant-180>
    </oppdrags-linje-150>
    <oppdrags-linje-150>
      <kodeEndringLinje>NY</kodeEndringLinje>
      <vedtakId>2025-04-10</vedtakId>
      <delytelseId>202503271001#17</delytelseId>
      <kodeKlassifik>TPTPAFT</kodeKlassifik>
      <datoVedtakFom>2025-01-30+01:00</datoVedtakFom>
      <datoVedtakTom>2025-01-30+01:00</datoVedtakTom>
      <sats>224</sats>
      <fradragTillegg>T</fradragTillegg>
      <typeSats>DAG7</typeSats>
      <brukKjoreplan>N</brukKjoreplan>
      <saksbehId>Z990123</saksbehId>
      <utbetalesTilId>14439535912</utbetalesTilId>
      <henvisning>22SK08N2GB3GQ7E</henvisning>
      <refFagsystemId>202503271001</refFagsystemId>
      <refDelytelseId>202503271001#16</refDelytelseId>
      <attestant-180>
        <attestantId>Z994127</attestantId>
      </attestant-180>
    </oppdrags-linje-150>
    <oppdrags-linje-150>
      <kodeEndringLinje>NY</kodeEndringLinje>
      <vedtakId>2025-04-10</vedtakId>
      <delytelseId>202503271001#18</delytelseId>
      <kodeKlassifik>TPBTAF</kodeKlassifik>
      <datoVedtakFom>2025-01-27+01:00</datoVedtakFom>
      <datoVedtakTom>2025-01-29+01:00</datoVedtakTom>
      <sats>110</sats>
      <fradragTillegg>T</fradragTillegg>
      <typeSats>DAG7</typeSats>
      <brukKjoreplan>N</brukKjoreplan>
      <saksbehId>Z990123</saksbehId>
      <utbetalesTilId>14439535912</utbetalesTilId>
      <henvisning>22SK08N2GB3GQ7E</henvisning>
      <refFagsystemId>202503271001</refFagsystemId>
      <refDelytelseId>202503271001#15</refDelytelseId>
      <attestant-180>
        <attestantId>Z994127</attestantId>
      </attestant-180>
    </oppdrags-linje-150>
    <oppdrags-linje-150>
      <kodeEndringLinje>NY</kodeEndringLinje>
      <vedtakId>2025-04-10</vedtakId>
      <delytelseId>202503271001#19</delytelseId>
      <kodeKlassifik>TPBTAF</kodeKlassifik>
      <datoVedtakFom>2025-01-30+01:00</datoVedtakFom>
      <datoVedtakTom>2025-01-30+01:00</datoVedtakTom>
      <sats>82</sats>
      <fradragTillegg>T</fradragTillegg>
      <typeSats>DAG7</typeSats>
      <brukKjoreplan>N</brukKjoreplan>
      <saksbehId>Z990123</saksbehId>
      <utbetalesTilId>14439535912</utbetalesTilId>
      <henvisning>22SK08N2GB3GQ7E</henvisning>
      <refFagsystemId>202503271001</refFagsystemId>
      <refDelytelseId>202503271001#18</refDelytelseId>
      <attestant-180>
        <attestantId>Z994127</attestantId>
      </attestant-180>
    </oppdrags-linje-150>
  </oppdrag-110>
</ns2:oppdrag>
""".trimIndent()

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
    private val weirdAsHellXmlDateFormatter = DateTimeFormatter.ofPattern("yyyyMMddHH")

    fun avstemmingXml(
        fagsystem: Fagsystem = Fagsystem.AAP,
        type: String = "DATA",
        fom: LocalDateTime = LocalDateTime.parse("2025-08-08-00.00.00.000000", formatter),
        tom: LocalDateTime = LocalDateTime.parse("2025-08-10-23.59.59.999999", formatter),
    ): String {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <ns2:avstemmingsdata xmlns:ns2="http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1">
                    <aksjon>
                        <aksjonType>$type</aksjonType>
                        <kildeType>AVLEV</kildeType>
                        <avstemmingType>GRSN</avstemmingType>
                        <avleverendeKomponentKode>${fagsystem.fagområde}</avleverendeKomponentKode>
                        <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                        <underkomponentKode>${fagsystem.fagområde}</underkomponentKode>
                        <nokkelFom>${fom.format(formatter)}</nokkelFom>
                        <nokkelTom>${tom.format(formatter)}</nokkelTom>
                        <avleverendeAvstemmingId>0no0lHFeR3C5-d1E1g6ynw</avleverendeAvstemmingId>
                        <brukerId>${fagsystem.fagområde}</brukerId>
                    </aksjon>
                    <total>
                        <totalAntall>9</totalAntall>
                        <totalBelop>9302</totalBelop>
                        <fortegn>T</fortegn>
                    </total>
                    <periode>
                        <datoAvstemtFom>${fom.format(weirdAsHellXmlDateFormatter)}</datoAvstemtFom>
                        <datoAvstemtTom>${tom.format(weirdAsHellXmlDateFormatter)}</datoAvstemtTom>
                    </periode>
                    <grunnlag>
                        <godkjentAntall>9</godkjentAntall>
                        <godkjentBelop>9302</godkjentBelop>
                        <godkjentFortegn>T</godkjentFortegn>
                        <varselAntall>0</varselAntall>
                        <varselBelop>0</varselBelop>
                        <varselFortegn>T</varselFortegn>
                        <avvistAntall>0</avvistAntall>
                        <avvistBelop>0</avvistBelop>
                        <avvistFortegn>T</avvistFortegn>
                        <manglerAntall>0</manglerAntall>
                        <manglerBelop>0</manglerBelop>
                        <manglerFortegn>T</manglerFortegn>
                    </grunnlag>
                </ns2:avstemmingsdata>
    
            """.trimIndent()
    }

//    fun utbetaling(
//        dryrun: Boolean = false,
//        originalKey: String = UUID.randomUUID().toString(),
//        fagsystem: Fagsystem = Fagsystem.AAP,
//        uid: UUID = UUID.randomUUID(),
//        action: Action = Action.CREATE,
//        førsteUtbetalingPåSak: Boolean = true,
//        sakId = TODO(),
//        behandlingId = TODO(),
//        lastPeriodeId = TODO(),
//        sistePeriode = TODO(),
//        personident = TODO(),
//        vedtakstidspunkt = TODO(),
//        stønad = TODO(),
//        beslutterId = TODO(),
//        saksbehandlerId = TODO(),
//        periodetype = TODO(),
//        avvent = TODO(),
//        perioder = TODO(),
//    ) =
//        Utbetaling(
//            dryrun = dryrun,
//            originalKey = originalKey,
//            fagsystem = fagsystem,
//            uid = UtbetalingId(uid),
//            action = action,
//            førsteUtbetalingPåSak = førsteUtbetalingPåSak,
//            sakId = TODO(),
//            behandlingId = TODO(),
//            lastPeriodeId = TODO(),
//            sistePeriode = TODO(),
//            personident = TODO(),
//            vedtakstidspunkt = TODO(),
//            stønad = TODO(),
//            beslutterId = TODO(),
//            saksbehandlerId = TODO(),
//            periodetype = TODO(),
//            avvent = TODO(),
//            perioder = TODO(),
//        )
}
