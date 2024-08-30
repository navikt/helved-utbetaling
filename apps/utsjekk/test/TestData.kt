import no.nav.utsjekk.kontrakter.felles.*
import no.nav.utsjekk.kontrakter.iverksett.*
import utsjekk.iverksetting.*
import java.time.LocalDate
import java.time.LocalDateTime

object TestData {
    object dao {
        fun iverksetting(
            behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
            iverksetting: Iverksetting = domain.iverksetting(behandlingId = behandlingId),
            mottattTidspunkt: LocalDateTime = LocalDateTime.now(),
        ): IverksettingDao = IverksettingDao(
            behandlingId = behandlingId,
            data = iverksetting,
            mottattTidspunkt = mottattTidspunkt,
        )

        fun iverksettingResultat(
            fagsystem: Fagsystem = Fagsystem.DAGPENGER,
            sakId: SakId = SakId(RandomOSURId.generate()),
            behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
            iverksettingId: IverksettingId? = null,
            tilkjentYtelse: TilkjentYtelse? = null,
            resultat: OppdragResultat? = null,
        ): IverksettingResultatDao = IverksettingResultatDao(
            fagsystem = fagsystem,
            sakId = sakId,
            behandlingId = behandlingId,
            iverksettingId = iverksettingId,
            tilkjentYtelseForUtbetaling = tilkjentYtelse,
            oppdragResultat = resultat,
        )
    }

    object dto {
        fun iverksetting(
            behandlingId: String = RandomOSURId.generate(),
            sakId: String = RandomOSURId.generate(),
            iverksettingId: String? = null,
            personident: Personident = Personident("15507600333"),
            vedtak: VedtaksdetaljerV2Dto = vedtaksdetaljer(),
            forrigeIverksetting: ForrigeIverksettingV2Dto? = null,
        ) = IverksettV2Dto(
            behandlingId = behandlingId,
            sakId = sakId,
            iverksettingId = iverksettingId,
            personident = personident,
            vedtak = vedtak,
            forrigeIverksetting = forrigeIverksetting,
        )

        fun vedtaksdetaljer(
            vedtakstidspunkt: LocalDateTime = LocalDateTime.of(2021, 5, 12, 0, 0),
            saksbehandlerId: String = "A12345",
            beslutterId: String = "B23456",
            utbetalinger: List<UtbetalingV2Dto> = listOf(utbetaling()),
        ) = VedtaksdetaljerV2Dto(
            vedtakstidspunkt = vedtakstidspunkt,
            saksbehandlerId = saksbehandlerId,
            beslutterId = beslutterId,
            utbetalinger = utbetalinger,
        )

        fun utbetaling(
            beløp: UInt = 500u,
            satstype: Satstype = Satstype.DAGLIG,
            fom: LocalDate = LocalDate.of(2021, 1, 1),
            tom: LocalDate = LocalDate.of(2021, 12, 31),
            stønadsdata: StønadsdataDto = dagpengestønad()
        ) =
            UtbetalingV2Dto(
                beløp = beløp,
                satstype = satstype,
                fraOgMedDato = fom,
                tilOgMedDato = tom,
                stønadsdata = stønadsdata
            )

        fun dagpengestønad(
            type: StønadTypeDagpenger = StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR,
            ferietillegg: Ferietillegg? = null,
        ) = StønadsdataDagpengerDto(type, ferietillegg)

        fun tilleggstønad(
            type: StønadTypeTilleggsstønader = StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
            brukersNavKontor: String? = null,
        ) = StønadsdataTilleggsstønaderDto(type, brukersNavKontor)
    }

    object domain {

        fun iverksetting(
            fagsystem: Fagsystem = Fagsystem.DAGPENGER,
            sakId: SakId = SakId(RandomOSURId.generate()),
            behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
            forrigeBehandlingId: BehandlingId? = null,
            iverksettingId: IverksettingId? = null,
            forrigeIverksettingId: IverksettingId? = null,
            andelsdatoer: List<LocalDate> = emptyList(),
            beløp: Int = 100,
            vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        ): Iverksetting {

            return Iverksetting(
                fagsak = Fagsakdetaljer(sakId, fagsystem),
                behandling = Behandlingsdetaljer(
                    behandlingId = behandlingId,
                    iverksettingId = iverksettingId,
                    forrigeBehandlingId = forrigeBehandlingId,
                    forrigeIverksettingId = forrigeIverksettingId,
                ),
                søker = Søker(
                    personident = "15507600333"
                ),
                vedtak = vedtaksdetaljer(
                    andeler = andelsdatoer.map {
                        enAndelTilkjentYtelse(beløp = beløp, fom = it, tom = it)
                    },
                    vedtakstidspunkt = vedtakstidspunkt
                )
            )
        }

        fun vedtaksdetaljer(
            andeler: List<AndelTilkjentYtelse> = listOf(enAndelTilkjentYtelse()),
            vedtakstidspunkt: LocalDateTime = LocalDateTime.of(2021, 5, 12, 0, 0),
        ): Vedtaksdetaljer = Vedtaksdetaljer(
            vedtakstidspunkt = vedtakstidspunkt,
            saksbehandlerId = "A12345",
            beslutterId = "B23456",
            tilkjentYtelse = enTilkjentYtelse(andeler),
        )

        fun enAndelTilkjentYtelse(
            beløp: Int = 5000,
            fom: LocalDate = LocalDate.of(2021, 1, 1),
            tom: LocalDate = LocalDate.of(2021, 12, 31),
            periodeId: Long? = null,
            forrigePeriodeId: Long? = null,
            stønadsdata: Stønadsdata = StønadsdataDagpenger(
                stønadstype = StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR
            )
        ): AndelTilkjentYtelse = AndelTilkjentYtelse(
            beløp = beløp,
            periode = Periode(fom, tom),
            periodeId = periodeId,
            forrigePeriodeId = forrigePeriodeId,
            stønadsdata = stønadsdata,
        )

        fun enTilkjentYtelse(
            andeler: List<AndelTilkjentYtelse>
        ): TilkjentYtelse = TilkjentYtelse(
            id = RandomOSURId.generate(),
            utbetalingsoppdrag = null,
            andelerTilkjentYtelse = andeler,
        )
    }


}
