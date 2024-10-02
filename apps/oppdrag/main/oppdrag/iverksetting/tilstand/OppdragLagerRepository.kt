package oppdrag.iverksetting.tilstand

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import libs.postgres.concurrency.connection
import libs.postgres.map
import libs.utils.appLog
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.trygdeetaten.skjema.oppdrag.Mmel
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext

object OppdragLagerRepository {

    suspend fun hentOppdrag(
        oppdragId: OppdragId,
        versjon: Int = 0,
    ): OppdragLager {
        val resultSet =
            if (oppdragId.iverksettingId != null) {
                coroutineContext.connection.prepareStatement(
                    """
                        SELECT * FROM oppdrag_lager 
                        WHERE behandling_id = ? 
                        AND fagsak_id = ? 
                        AND fagsystem = ? 
                        AND iverksetting_id = ? 
                        AND versjon = ?
                    """.trimIndent()
                ).apply {
                    setObject(1, oppdragId.behandlingId)
                    setObject(2, oppdragId.fagsakId)
                    setObject(3, oppdragId.fagsystem.kode)
                    setObject(4, oppdragId.iverksettingId)
                    setObject(5, versjon)
                }.executeQuery()
            } else {
                coroutineContext.connection.prepareStatement(
                    """
                        SELECT * FROM oppdrag_lager 
                        WHERE behandling_id = ? 
                        AND fagsak_id = ? 
                        AND fagsystem = ? 
                        AND iverksetting_id is null 
                        AND versjon = ?
                    """.trimIndent()
                ).apply {
                    setObject(1, oppdragId.behandlingId)
                    setObject(2, oppdragId.fagsakId)
                    setObject(3, oppdragId.fagsystem.kode)
                    setObject(4, versjon)
                }.executeQuery()
            }

        val listeAvOppdrag = resultSet.map {
            oppdragLager(it)
        }

        return when (listeAvOppdrag.size) {
            0 -> {
                appLog.error("Feil ved henting av oppdrag. Fant ingen oppdrag med id $oppdragId")
                throw NoSuchElementException("Feil ved henting av oppdrag. Fant ingen oppdrag med id $oppdragId")
            }

            1 -> listeAvOppdrag.single()
            else -> {
                appLog.error("Feil ved henting av oppdrag. Fant fler oppdrag med id $oppdragId")
                error("Feil ved henting av oppdrag. Fant fler oppdrag med id $oppdragId")
            }
        }
    }


    suspend fun opprettOppdrag(
        oppdragLager: OppdragLager,
        versjon: Int = 0,
    ) {
        coroutineContext.connection.prepareStatement(
            """
                INSERT INTO oppdrag_lager (
                    id, 
                    utgaaende_oppdrag, 
                    status, 
                    opprettet_tidspunkt, 
                    fagsak_id, 
                    behandling_id, 
                    iverksetting_id, 
                    fagsystem, 
                    avstemming_tidspunkt, 
                    utbetalingsoppdrag, 
                    versjon
                ) 
                VALUES (?,?,?,?,?,?,?,?,?,?::JSON,?)
            """.trimIndent()
        ).apply {
            setObject(1, UUID.randomUUID())
            setObject(2, oppdragLager.utgaaende_oppdrag)
            setObject(3, oppdragLager.status.name)
            setObject(4, oppdragLager.opprettet_tidspunkt)
            setObject(5, oppdragLager.fagsak_id)
            setObject(6, oppdragLager.behandling_id)
            setObject(7, oppdragLager.iverksetting_id)
            setObject(8, oppdragLager.fagsystem)
            setObject(9, oppdragLager.avstemming_tidspunkt)
            setString(10, jackson.writeValueAsString(oppdragLager.utbetalingsoppdrag))
            setObject(11, versjon)
        }.executeUpdate()
    }

    suspend fun oppdaterStatus(
        oppdragId: OppdragId,
        oppdragStatus: OppdragStatus,
        versjon: Int = 0,
    ) {
        coroutineContext.connection.prepareStatement(
            """
            UPDATE oppdrag_lager 
            SET status = ?
            WHERE fagsak_id = ? 
                AND fagsystem = ? 
                AND behandling_id = ?
                AND versjon = ?
            """.trimIndent()
        ).apply {
            setObject(1, oppdragStatus.name)
            setObject(2, oppdragId.fagsakId)
            setObject(3, oppdragId.fagsystem.kode)
            setObject(4, oppdragId.behandlingId)
            setObject(5, versjon)
        }.executeUpdate()
    }

    suspend fun oppdaterKvitteringsmelding(
        oppdragId: OppdragId,
        kvittering: Mmel,
        versjon: Int = 0,
    ) {
        coroutineContext.connection.prepareStatement(
            """
            UPDATE oppdrag_lager 
            SET kvitteringsmelding = ?::JSON 
            WHERE fagsak_id = ? 
            AND fagsystem = ? 
            AND behandling_id = ? 
            AND versjon = ?
            """.trimIndent()
        ).apply {
            setObject(1, jackson.writeValueAsString(kvittering))
            setObject(2, oppdragId.fagsakId)
            setObject(3, oppdragId.fagsystem.kode)
            setObject(4, oppdragId.behandlingId)
            setObject(5, versjon)
        }.executeUpdate()
        appLog.debug("Updated oppdrag-Lager with kvitteringsmelding for oppdragId: {}", oppdragId)
    }

    suspend fun hentIverksettingerForGrensesnittavstemming(
        fomTidspunkt: LocalDateTime,
        tomTidspunkt: LocalDateTime,
        fagsystem: Fagsystem,
    ): List<OppdragLager> {
        return coroutineContext.connection.prepareStatement(
            """
                SELECT * FROM oppdrag_lager 
                WHERE avstemming_tidspunkt >= ? 
                AND avstemming_tidspunkt < ? 
                AND fagsystem = ?
            """.trimIndent()
        ).apply {
            setObject(1, fomTidspunkt)
            setObject(2, tomTidspunkt)
            setObject(3, fagsystem.kode)
        }.executeQuery().map {
            oppdragLager(it)
        }
    }

    suspend fun hentAlleVersjonerAvOppdrag(
        oppdragId: OppdragId,
    ): List<OppdragLager> {
        return coroutineContext.connection.prepareStatement(
            """
               SELECT * FROM oppdrag_lager 
               WHERE behandling_id = ? 
               AND fagsak_id = ? 
               AND fagsystem = ?
            """.trimIndent()
        ).apply {
            setObject(1, oppdragId.behandlingId)
            setObject(2, oppdragId.fagsakId)
            setObject(3, oppdragId.fagsystem.kode)
        }.executeQuery().map {
            oppdragLager(it)
        }
    }
}

private fun oppdragLager(it: ResultSet): OppdragLager {
    val kvittering: String? = it.getString("kvitteringsmelding")
    val utbetalingsoppdrag = it.getString("utbetalingsoppdrag")

    return OppdragLager(
        uuid = UUID.fromString(it.getString("id") ?: UUID.randomUUID().toString()),
        fagsystem = it.getString("fagsystem"),
        fagsak_id = it.getString("fagsak_id"),
        behandling_id = it.getString("behandling_id"),
        iverksetting_id = it.getString("iverksetting_id"),
        utbetalingsoppdrag = jackson.readValue(utbetalingsoppdrag),
        utgaaende_oppdrag = it.getString("utgaaende_oppdrag"),
        status = OppdragStatus.valueOf(it.getString("status")),
        avstemming_tidspunkt = it.getTimestamp("avstemming_tidspunkt").toLocalDateTime(),
        opprettet_tidspunkt = it.getTimestamp("opprettet_tidspunkt").toLocalDateTime(),
        kvitteringsmelding = kvittering?.let(jackson::readValue),
        versjon = it.getInt("versjon"),
    )
}

private val jackson: ObjectMapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}
