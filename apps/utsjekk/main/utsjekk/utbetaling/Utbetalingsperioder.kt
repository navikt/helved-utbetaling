package utsjekk.utbetaling

import java.time.LocalDate


object Utbetalingsperioder {
    fun utled(existing: Utbetaling, new: Utbetaling): List<UtbetalingsperiodeDto> {
        val existing = existing.copy(perioder = existing.perioder.sortedBy { it.fom })
        val new = new.copy(perioder = new.perioder.sortedBy { it.fom })

        val opphørsdato = opphørsdato(existing.perioder, new.perioder)
        val nyeLinjer = nyeLinjer(existing, new, opphørsdato)

        return if (opphørsdato != null) {
            listOf(opphørslinje(new, existing.perioder.last(), existing.lastPeriodeId, opphørsdato)) + nyeLinjer
        } else {
            nyeLinjer
        }
    }

    private fun nyeLinjer(
        existing: Utbetaling,
        new: Utbetaling,
        opphørsdato: LocalDate?,
    ): List<UtbetalingsperiodeDto> {
        var førsteEndringIdx = existing.perioder.zip(new.perioder).indexOfFirst { it.first != it.second }

        when {
            førsteEndringIdx == -1 && new.perioder.size > existing.perioder.size -> { 
                // De(n) nye endringen(e) kommer etter siste eksisterende periode.
                førsteEndringIdx = existing.perioder.size
            }
            førsteEndringIdx == -1 -> {
                return emptyList()
            }
            else -> {
                // Om første endring er en forkorting av tom ønsker vi ikke sende med denne som en ny utbetalingslinje.
                // Opphørslinjen tar ansvar for forkortingen av perioden, og vi ønsker bare å sende med alt etter perioden
                // som har endret seg.
                if (existing.perioder[førsteEndringIdx].tom > new.perioder[førsteEndringIdx].tom
                    && existing.perioder[førsteEndringIdx].beløp == new.perioder[førsteEndringIdx].beløp
                    && existing.perioder[førsteEndringIdx].fom == new.perioder[førsteEndringIdx].fom
                ) {
                    førsteEndringIdx += 1
                }
            }
        }

        var sistePeriodeId = existing.lastPeriodeId
        return new.perioder
            .slice(førsteEndringIdx until new.perioder.size)
            .filter { if (opphørsdato != null) it.fom >= opphørsdato else true }
            .map { p ->
                val pid = PeriodeId()
                utbetalingslinje(
                    utbetaling = new,
                    periode = p,
                    periodeId = pid,
                    forrigePeriodeId = sistePeriodeId,
                ).also {
                    sistePeriodeId = pid
                }
            }
    }

    private fun opphørsdato(existing: List<Utbetalingsperiode>, new: List<Utbetalingsperiode>): LocalDate? {
        if (new.first().fom > existing.first().fom) {
            // Forkorter fom i starten. Må opphøre fra start
            return existing.first().fom
        }

        // Hvis det finnes et mellomrom må vi opphøre fra starten av mellomrommet
        val opphørsdato = new
            .windowed(2)
            .find { it.first().tom < it.last().fom.minusDays(1) }
            ?.first()?.tom?.plusDays(1)

        // Hvis vi ikke har opphørsdato i et mellomrom kan det hende at den siste perioden i
        // ny utbetaling er kortere enn siste perioden i eksisterende utbetaling
        return opphørsdato ?: if (new.last().tom < existing.last().tom) new.last().tom.plusDays(
            1
        ) else null
    }

    private fun utbetalingslinje(
        utbetaling: Utbetaling,
        periode: Utbetalingsperiode,
        periodeId: PeriodeId,
        forrigePeriodeId: PeriodeId,
    ): UtbetalingsperiodeDto {
        return UtbetalingsperiodeDto(
            erEndringPåEksisterendePeriode = false,
            id = periodeId.toString(),
            forrigePeriodeId = forrigePeriodeId.toString(),
            vedtaksdato = utbetaling.vedtakstidspunkt.toLocalDate(),
            klassekode = klassekode(utbetaling.stønad),
            fom = periode.fom,
            tom = periode.tom,
            sats = periode.beløp,
            satstype = periode.satstype,
            utbetalesTil = utbetaling.personident.ident,
            behandlingId = utbetaling.behandlingId.id,
            fastsattDagsats = periode.fastsattDagsats,
        )
    }

    private fun opphørslinje(
        new: Utbetaling,
        sistePeriode: Utbetalingsperiode,
        periodeId: PeriodeId,
        opphørsdato: LocalDate,
    ): UtbetalingsperiodeDto {
        return UtbetalingsperiodeDto(
            erEndringPåEksisterendePeriode = true,
            id = periodeId.toString(),
            opphør = Opphør(opphørsdato),
            vedtaksdato = new.vedtakstidspunkt.toLocalDate(),
            klassekode = klassekode(new.stønad),
            fom = sistePeriode.fom,
            tom = sistePeriode.tom,
            sats = sistePeriode.beløp,
            satstype = sistePeriode.satstype,
            utbetalesTil = new.personident.ident,
            behandlingId = new.behandlingId.id,
            fastsattDagsats = sistePeriode.fastsattDagsats,
        )
    }
}
