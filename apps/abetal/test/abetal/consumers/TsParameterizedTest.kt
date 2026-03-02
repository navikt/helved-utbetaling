package abetal.consumers

import abetal.*
import models.*
import org.junit.jupiter.api.DynamicTest

/**
 * Parameterized tests for Tilleggsstønader consumer.
 * 
 * Runs common test scenarios with TS-specific configuration.
 * Uses TILLSTPB fagområde (Tilsyn barn).
 * 
 * NOTE: The "multiple periods" test is disabled because TS groups periods
 * into a single utbetaling, unlike DP which creates one utbetaling per period.
 */
internal class TsParameterizedTest : ConsumerParameterizedTestBase<TsDto>() {
    
    override val fagsystem = Fagsystem.TILLSTPB
    override val fagområde = "TILLSTPB"
    override val saksbehId = "ts"
    
    // TS uses TILLEGGSSTØNADER as the saker topic key, even though individual
    // utbetalinger use specific fagsystems (TILLSTPB, TILLSTLM, etc.)
    override fun getSakerFagsystem(): Fagsystem = Fagsystem.TILLEGGSSTØNADER
    
    override fun getDefaultPeriodetype(): Periodetype = Periodetype.EN_GANG
    
    // Disable tests that don't apply to TS
    override fun `multiple periods create multiple utbetalinger`() = emptyList<DynamicTest>()
    override fun `update existing utbetaling`() = emptyList<DynamicTest>()
    
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
}
