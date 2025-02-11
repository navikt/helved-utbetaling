package abetal

import abetal.models.*
import java.time.LocalDate
import java.time.LocalDateTime

object TestData {
    fun utbetaling(
        stønad: Stønadstype,
        periodetype: Periodetype,
        perioder: List<Utbetalingsperiode>,
        sakId: SakId = SakId("1"),
        behandlingId: BehandlingId = BehandlingId(""),
        lastPeriodeId: PeriodeId = PeriodeId(),
        personident: Personident = Personident(""),
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        beslutterId: Navident = Navident(""),
        saksbehandlerId: Navident = Navident(""),
    ) = Utbetaling(
        sakId = sakId,
        behandlingId = behandlingId,
        lastPeriodeId = lastPeriodeId,
        personident = personident,
        vedtakstidspunkt = vedtakstidspunkt,
        stønad = stønad,
        beslutterId = beslutterId,
        saksbehandlerId = saksbehandlerId,
        periodetype = periodetype,
        perioder = perioder
    )

    fun utbetalingsperiode(
        fom: LocalDate,
        tom: LocalDate,
        beløp: UInt = 123u,
        fastsattDagsats: UInt? = null
    ) = Utbetalingsperiode(
        fom = fom,
        tom = tom,
        beløp = beløp,
        fastsattDagsats = fastsattDagsats,
    )

    fun dag(dato: LocalDate, beløp: UInt = 123u, fastsattDagsats: UInt? = 123u) =
        utbetalingsperiode(dato, dato, beløp, fastsattDagsats)
}