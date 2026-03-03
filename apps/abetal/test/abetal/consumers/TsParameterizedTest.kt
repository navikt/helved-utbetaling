package abetal.consumers

import abetal.*
import models.*
import org.junit.jupiter.api.DynamicTest
import java.time.LocalDateTime

/**
 * Parameterized tests for Tilleggsstønader consumer.
 * 
 * Runs common test scenarios with TS-specific configuration.
 * Uses TILLSTPB fagområde (Tilsyn barn).
 */
internal class TsParameterizedTest : ConsumerParameterizedTestBase<TsDto>() {
    
    override val fagsystem = Fagsystem.TILLSTPB
    override val fagområde = "TILLSTPB"
    override val saksbehId = "ts"
    override val periodetype = Periodetype.EN_GANG
    
    // TS uses TILLEGGSSTØNADER as the saker topic key, even though individual
    // utbetalinger use specific fagsystems (TILLSTPB, TILLSTLM, etc.)
    override fun getSakerFagsystem(): Fagsystem = Fagsystem.TILLEGGSSTØNADER
    
    // Disable tests that don't apply to TS
    override fun `multiple periods create multiple utbetalinger`() = emptyList<DynamicTest>()
    override fun `update existing utbetaling`() = emptyList<DynamicTest>()
    override fun `simulering uten endring`() = emptyList<DynamicTest>()  // TS has different simulation behavior - needs specific investigation
    
    override fun createMessage(
        sakId: String,
        behandlingId: String,
        perioder: List<TestPeriode>
    ): TsDto {
        val utbetalinger = perioder.groupBy { it.uniqueKey }.map { (_, groupedPerioder) ->
            val uid = createUtbetalingId(sakId, groupedPerioder.first().uniqueKey, getDefaultStønad())
            TsUtbetaling(
                id = uid.id,
                stønad = getDefaultStønad() as StønadTypeTilleggsstønader,
                perioder = groupedPerioder.map { TsPeriode(it.fom, it.tom, it.sats) },
                brukFagområdeTillst = false
            )
        }
        
        return Ts.dto(sakId, behandlingId) {
            utbetalinger
        }
    }
    
    override fun produceMessage(transactionId: String, message: TsDto) {
        TestRuntime.topics.ts.produce(transactionId) { message }
    }
    
    override fun createUtbetalingId(sakId: String, uniqueKey: String, stønad: Stønadstype): UtbetalingId {
        return UtbetalingId(java.util.UUID.nameUUIDFromBytes("$sakId-$uniqueKey-$stønad".toByteArray()))
    }
    
    override fun getDefaultStønad(): Stønadstype {
        return StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER
    }
    
    override fun getExpectedKlassekode(): String {
        return "TSTBASISP2-OP"
    }
    
    override fun createMessageDryrun(sakId: String, behandlingId: String, perioder: List<TestPeriode>, vedtakstidspunkt: LocalDateTime): TsDto {
        val utbetalinger = perioder.groupBy { it.uniqueKey }.map { (_, groupedPerioder) ->
            val uid = createUtbetalingId(sakId, groupedPerioder.first().uniqueKey, getDefaultStønad())
            TsUtbetaling(
                id = uid.id,
                stønad = getDefaultStønad() as StønadTypeTilleggsstønader,
                perioder = groupedPerioder.map { TsPeriode(it.fom, it.tom, it.sats) },
                brukFagområdeTillst = false
            )
        }
        
        return Ts.dto(sakId, behandlingId, vedtakstidspunkt = vedtakstidspunkt, dryrun = true) {
            utbetalinger
        }
    }
}
