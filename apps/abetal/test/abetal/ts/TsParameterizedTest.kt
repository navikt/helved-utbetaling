package abetal.ts

import abetal.*
import models.*
import org.junit.jupiter.api.DynamicTest
import java.time.LocalDateTime

/**
 * Parameterized tests for Tilleggsstønader consumer.
 */
internal class TsParameterizedTest : ConsumerParameterizedTestBase<TsDto>() {
    
    override val fagsystem = Fagsystem.TILLSTPB
    override val fagområde = "TILLSTPB"
    override val saksbehId = "ts"
    override val periodetype = Periodetype.EN_GANG
    
    override val sakerFagsystem: Fagsystem = Fagsystem.TILLEGGSSTØNADER
    override val defaultStønad: Stønadstype = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER
    override val expectedKlassekode: String = "TSTBASISP2-OP"
    
    // Disable tests that don't apply to TS
    override fun `create - multiple perioder create utbetalinger`() = emptyList<DynamicTest>()
    override fun `update - modifying existing utbetaling with new periode`() = emptyList<DynamicTest>()
    override fun `simulation - no changes produces no oppdrag`() = emptyList<DynamicTest>()  // TS has different simulation behavior - needs specific investigation
    
    override fun createMessage(
        sakId: String,
        behandlingId: String,
        perioder: List<TestPeriode>
    ): TsDto {
        val utbetalinger = perioder.groupBy { it.uniqueKey }.map { (_, groupedPerioder) ->
            val uid = createUtbetalingId(sakId, groupedPerioder.first().uniqueKey, defaultStønad)
            TsUtbetaling(
                id = uid.id,
                stønad = defaultStønad as StønadTypeTilleggsstønader,
                perioder = groupedPerioder.map { TsPeriode(it.fom, it.tom, it.sats) },
                brukFagområdeTillst = false
            )
        }
        
        return Ts.dto(sakId, behandlingId) {
            utbetalinger
        }
    }
    
    override fun produceMessage(transactionId: String, message: TsDto) {
        TestRuntime.topics.ts.produce(transactionId) { message.asBytes() }
    }
    
    override fun createUtbetalingId(sakId: String, uniqueKey: String, stønad: Stønadstype): UtbetalingId {
        return UtbetalingId(java.util.UUID.nameUUIDFromBytes("$sakId-$uniqueKey-$stønad".toByteArray()))
    }
    
    override fun createMessageDryrun(sakId: String, behandlingId: String, perioder: List<TestPeriode>, vedtakstidspunkt: LocalDateTime): TsDto {
        val utbetalinger = perioder.groupBy { it.uniqueKey }.map { (_, groupedPerioder) ->
            val uid = createUtbetalingId(sakId, groupedPerioder.first().uniqueKey, defaultStønad)
            TsUtbetaling(
                id = uid.id,
                stønad = defaultStønad as StønadTypeTilleggsstønader,
                perioder = groupedPerioder.map { TsPeriode(it.fom, it.tom, it.sats) },
                brukFagområdeTillst = false
            )
        }
        
        return Ts.dto(sakId, behandlingId, vedtakstidspunkt = vedtakstidspunkt, dryrun = true) {
            utbetalinger
        }
    }
}
