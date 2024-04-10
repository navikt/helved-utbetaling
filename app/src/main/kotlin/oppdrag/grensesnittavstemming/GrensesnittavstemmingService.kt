package oppdrag.grensesnittavstemming

import felles.log.appLog
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.dagpenger.kontrakter.felles.Fagsystem
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Grunnlagsdata
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import oppdrag.postgres.transaction
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class GrensesnittavstemmingService(
    private val avstemmingSender: GrensesnittavstemmingSender,
    private val postgres: DataSource,
) {
    private val tellere: MutableMap<Fagsystem, Map<String, Counter>> = EnumMap(Fagsystem::class.java)

    init {
        enumValues<Fagsystem>().forEach {
            tellere[it] = metrikkerForFagsystem(it)
        }
    }

    fun utførGrensesnittavstemming(
        fagsystem: Fagsystem,
        fra: LocalDateTime,
        til: LocalDateTime,
    ) {
        postgres.transaction { con ->
            val oppdragSomSkalAvstemmes = OppdragLagerRepository.hentIverksettingerForGrensesnittavstemming(
                fomTidspunkt = fra,
                tomTidspunkt = til,
                fagsystem = fagsystem,
                con,
            )

            val avstemmingMapper = GrensesnittavstemmingMapper(oppdragSomSkalAvstemmes, fagsystem, fra, til)
            val meldinger = avstemmingMapper.lagAvstemmingsmeldinger()

            if (meldinger.isEmpty()) {
                appLog.info("Ingen oppdrag å gjennomføre grensesnittavstemming for.")
                return@transaction
            }

            appLog.info("Utfører grensesnittavstemming for id: ${avstemmingMapper.avstemmingId}, ${meldinger.size} antall meldinger.")

            meldinger.forEach {
                avstemmingSender.sendGrensesnittAvstemming(it)
            }

            appLog.info("Fullført grensesnittavstemming for id: ${avstemmingMapper.avstemmingId}")

            oppdaterMetrikker(fagsystem, meldinger[1].grunnlag)
        }
    }

    private fun oppdaterMetrikker(
        fagsystem: Fagsystem,
        grunnlag: Grunnlagsdata,
    ) {
        val metrikkerForFagsystem = tellere.getValue(fagsystem)

        metrikkerForFagsystem.getValue(Status.GODKJENT.status).increment(grunnlag.godkjentAntall.toDouble())
        metrikkerForFagsystem.getValue(Status.AVVIST.status).increment(grunnlag.avvistAntall.toDouble())
        metrikkerForFagsystem.getValue(Status.MANGLER.status).increment(grunnlag.manglerAntall.toDouble())
        metrikkerForFagsystem.getValue(Status.VARSEL.status).increment(grunnlag.varselAntall.toDouble())
    }

    private fun metrikkerForFagsystem(fagsystem: Fagsystem) =
        hashMapOf(
            Status.GODKJENT.status to tellerForFagsystem(fagsystem, Status.GODKJENT),
            Status.AVVIST.status to tellerForFagsystem(fagsystem, Status.AVVIST),
            Status.MANGLER.status to tellerForFagsystem(fagsystem, Status.MANGLER),
            Status.VARSEL.status to tellerForFagsystem(fagsystem, Status.VARSEL),
        )

    private fun tellerForFagsystem(
        fagsystem: Fagsystem,
        status: Status,
    ) = Metrics.counter(
        "dagpenger.oppdrag.grensesnittavstemming",
        "fagsystem",
        fagsystem.name,
        "status",
        status.status,
        "beskrivelse",
        status.beskrivelse,
    )
}

enum class Status(val status: String, val beskrivelse: String) {
    GODKJENT("godkjent", "Antall oppdrag som har fått OK kvittering (alvorlighetsgrad 00)."),
    AVVIST(
        "avvist",
        "Antall oppdrag som har fått kvittering med funksjonell eller teknisk feil, samt ukjent (alvorlighetsgrad 08 og 12).",
    ),
    MANGLER("mangler", "Antall oppdrag hvor kvittering mangler."),
    VARSEL("varsel", "Antall oppdrag som har fått kvittering med mangler (alvorlighetsgrad 04)."),
}
