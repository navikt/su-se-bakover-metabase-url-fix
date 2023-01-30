package no.nav.su.se.bakover.test

import arrow.core.Tuple4
import io.kotest.assertions.fail
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.domain.revurdering.toVedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.iverksett.IverksettInnvilgetRevurderingResponse
import no.nav.su.se.bakover.domain.sak.iverksett.IverksettOpphørtRevurderingResponse
import no.nav.su.se.bakover.domain.sak.iverksett.IverksettRevurderingResponse
import no.nav.su.se.bakover.domain.sak.iverksett.iverksettRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

val revurderingId: UUID = UUID.randomUUID()

val oppgaveIdRevurdering = OppgaveId("oppgaveIdRevurdering")

/** MELDING_FRA_BRUKER */
val revurderingsårsak =
    Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("revurderingsårsakBegrunnelse"),
    )

fun opprettRevurderingFraSaksopplysninger(
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes>,
    clock: Clock = tikkendeFixedClock(),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
): Pair<Sak, OpprettetRevurdering> {
    vilkårOverrides.map { it::class }.let {
        require(it == it.distinct())
    }
    require(vilkårOverrides.none { it.vurdering == Vurdering.Uavklart }) {
        "Man kan ikke sende inn uavklarte vilkår til en revurdering. Den starter som utfylt(innvilget/avslag) også kan man overskrive de med nye vilkår som er innvilget/avslag, men ikke uavklart."
    }
    return sakOgVedtakSomKanRevurderes.first.opprettRevurdering(
        command = OpprettRevurderingCommand(
            sakId = sakOgVedtakSomKanRevurderes.first.id,
            periode = revurderingsperiode,
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            saksbehandler = saksbehandler,
            informasjonSomRevurderes = informasjonSomRevurderes.informasjonSomRevurderes.keys.toList(),
        ),
        clock = clock,
    ).getOrFail().leggTilOppgaveId(oppgaveIdRevurdering).let { (sak, opprettetRevurdering) ->
        opprettetRevurdering.let { or ->
            vilkårOverrides.filterIsInstance<UføreVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterUføreOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            vilkårOverrides.filterIsInstance<FlyktningVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterFlyktningvilkårOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            grunnlagsdataOverrides.filterIsInstance<Grunnlag.Bosituasjon>().let {
                if (it.isNotEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    or.oppdaterBosituasjonOgMarkerSomVurdert(it as List<Grunnlag.Bosituasjon.Fullstendig>).getOrFail()
                } else {
                    or
                }
            }
        }.let { or ->
            vilkårOverrides.filterIsInstance<FastOppholdINorgeVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterFastOppholdINorgeOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            vilkårOverrides.filterIsInstance<FormueVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterFormueOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            vilkårOverrides.filterIsInstance<UtenlandsoppholdVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterUtenlandsoppholdOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            grunnlagsdataOverrides.filterIsInstance<Grunnlag.Fradragsgrunnlag>().let {
                if (it.isNotEmpty()) {
                    or.oppdaterFradragOgMarkerSomVurdert(it).getOrFail()
                } else {
                    or
                }
            }
        }.let { or ->
            vilkårOverrides.filterIsInstance<OpplysningspliktVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterOpplysningspliktOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            vilkårOverrides.filterIsInstance<LovligOppholdVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterLovligOppholdOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            vilkårOverrides.filterIsInstance<PersonligOppmøteVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            vilkårOverrides.filterIsInstance<InstitusjonsoppholdVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterInstitusjonsoppholdOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { r ->
            sak.copy(
                revurderinger = sak.revurderinger.filterNot { it.id == r.id }.plus(r),
            ) to r
        }
    }
}

/**
 * @param stønadsperiode brukes kun dersom [sakOgVedtakSomKanRevurderes] får default-verdi.
 * @param sakOgVedtakSomKanRevurderes Dersom denne settes, ignoreres [stønadsperiode]
 */
fun opprettetRevurdering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    clock: Clock = tikkendeFixedClock(),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
): Pair<Sak, OpprettetRevurdering> {
    return opprettRevurderingFraSaksopplysninger(
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
        revurderingsårsak = revurderingsårsak,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
    )
}

fun beregnetRevurdering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    clock: Clock = tikkendeFixedClock(),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
): Pair<Sak, BeregnetRevurdering> {
    return opprettetRevurdering(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsårsak = revurderingsårsak,
        clock = clock,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
    ).let { (sak, opprettet) ->
        val beregnet = opprettet.beregn(
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = clock,
            gjeldendeVedtaksdata = sak.hentGjeldendeVedtaksdata(
                periode = opprettet.periode,
                clock = clock,
            ).getOrFail(),
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail()

        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == beregnet.id } + beregnet,
        ) to beregnet
    }
}

fun simulertRevurdering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    clock: Clock = tikkendeFixedClock(),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    brevvalg: BrevvalgRevurdering = sendBrev(),
): Pair<Sak, SimulertRevurdering> {
    return beregnetRevurdering(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsårsak = revurderingsårsak,
        clock = clock,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
    ).let { (sak, beregnet) ->
        val simulert = when (beregnet) {
            is BeregnetRevurdering.Innvilget -> {
                val simulert = beregnet.simuler(
                    saksbehandler = saksbehandler,
                    clock = clock,
                    simuler = { _, _ ->
                        simulerUtbetaling(
                            sak = sak,
                            revurdering = beregnet,
                            simuleringsperiode = beregnet.periode,
                            clock = clock,
                            utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
                        ).map {
                            it.simulering
                        }
                    },
                ).getOrFail()
                oppdaterTilbakekrevingsbehandling(simulert)
            }

            is BeregnetRevurdering.Opphørt -> {
                val simulert = beregnet.simuler(
                    saksbehandler = saksbehandler,
                    clock = clock,
                    simuler = { periode, saksbehandler ->
                        simulerOpphør(
                            sak = sak,
                            revurdering = beregnet,
                            simuleringsperiode = periode,
                            behandler = saksbehandler,
                            clock = clock,
                            utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
                        )
                    },
                ).getOrFail()
                oppdaterTilbakekrevingsbehandling(simulert)
            }
        }.leggTilBrevvalg(brevvalg).getOrFail() as SimulertRevurdering

        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == simulert.id } + simulert,
        ) to simulert
    }
}

fun revurderingTilAttestering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    clock: Clock = tikkendeFixedClock(),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    brevvalg: BrevvalgRevurdering = sendBrev(),
): Pair<Sak, RevurderingTilAttestering> {
    return simulertRevurdering(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
        revurderingsårsak = revurderingsårsak,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
        brevvalg = brevvalg,
    ).let { (sak, simulert) ->
        val tilAttestering = when (simulert) {
            is SimulertRevurdering.Innvilget -> {
                simulert.tilAttestering(
                    attesteringsoppgaveId = attesteringsoppgaveId,
                    saksbehandler = saksbehandler,
                ).getOrFail()
            }

            is SimulertRevurdering.Opphørt -> {
                simulert.tilAttestering(
                    attesteringsoppgaveId = attesteringsoppgaveId,
                    saksbehandler = saksbehandler,
                ).getOrFail()
            }
        }
        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == tilAttestering.id } + tilAttestering,
        ) to tilAttestering
    }
}

fun revurderingUnderkjent(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    clock: Clock = tikkendeFixedClock(),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    attestering: Attestering.Underkjent = attesteringUnderkjent(clock),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
): Pair<Sak, UnderkjentRevurdering> {
    return revurderingTilAttestering(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsårsak = revurderingsårsak,
        clock = clock,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    ).let { (sak, tilAttestering) ->
        val underkjent = tilAttestering.underkjenn(
            attestering = attestering,
            oppgaveId = OppgaveId("underkjentOppgaveId"),
        )
        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == tilAttestering.id } + underkjent,
        ) to underkjent
    }
}

private fun oppdaterTilbakekrevingsbehandling(revurdering: SimulertRevurdering): SimulertRevurdering {
    return when (revurdering.simulering.harFeilutbetalinger()) {
        true -> {
            revurdering.oppdaterTilbakekrevingsbehandling(
                tilbakekrevingsbehandling = Tilbakekrev(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    sakId = revurdering.sakId,
                    revurderingId = revurdering.id,
                    periode = revurdering.periode,
                ),
            )
        }

        false -> {
            revurdering.oppdaterTilbakekrevingsbehandling(
                tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
            )
        }
    }
}

fun iverksattRevurdering(
    clock: Clock = tikkendeFixedClock(),
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    attestant: NavIdentBruker.Attestant = no.nav.su.se.bakover.test.attestant,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    brevvalg: BrevvalgRevurdering = sendBrev(),
): Tuple4<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.UtenKvittering, VedtakSomKanRevurderes.EndringIYtelse> {
    return revurderingTilAttestering(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsårsak = revurderingsårsak,
        clock = clock,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
        saksbehandler = saksbehandler,
        attesteringsoppgaveId = attesteringsoppgaveId,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
        brevvalg = brevvalg,
    ).let { (sak, tilAttestering) ->
        sak.iverksettRevurdering(
            revurderingId = tilAttestering.id,
            attestant = attestant,
            clock = clock,
            simuler = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode ->
                simulerUtbetaling(
                    sak = sak,
                    utbetaling = utbetalingForSimulering,
                    simuleringsperiode = periode,
                    clock = clock,
                    utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
                )
            },
        ).getOrFail().let { response ->
            /**
             * TODO
             * se om vi får til noe som oppfører seg som [IverksettRevurderingResponse.ferdigstillIverksettelseITransaksjon]?
             */
            val oversendtUtbetaling =
                response.utbetaling.toOversendtUtbetaling(UtbetalingStub.generateRequest(response.utbetaling))

            Tuple4(
                first = response.sak.copy(utbetalinger = response.sak.utbetalinger.filterNot { it.id == oversendtUtbetaling.id } + oversendtUtbetaling),
                second = when (response) {
                    is IverksettInnvilgetRevurderingResponse -> {
                        response.vedtak.behandling
                    }

                    is IverksettOpphørtRevurderingResponse -> {
                        response.vedtak.behandling
                    }

                    else -> TODO("Ukjent implementasjon av ${IverksettRevurderingResponse::class}")
                },
                third = oversendtUtbetaling,
                fourth = response.vedtak,
            )
        }
    }
}

fun vedtakRevurdering(
    clock: Clock = tikkendeFixedClock(),
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    attestant: NavIdentBruker.Attestant = no.nav.su.se.bakover.test.attestant,
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    brevvalg: BrevvalgRevurdering = sendBrev(),
): Pair<Sak, VedtakSomKanRevurderes> {
    return iverksattRevurdering(
        clock = clock,
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsårsak = revurderingsårsak,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
        attestant = attestant,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
        brevvalg = brevvalg,
    ).let {
        it.first to it.fourth
    }
}

fun lagFradragsgrunnlag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    type: Fradragstype,
    månedsbeløp: Double,
    periode: Periode,
    utenlandskInntekt: UtenlandskInntekt? = null,
    tilhører: FradragTilhører,
) = Grunnlag.Fradragsgrunnlag.tryCreate(
    id = id,
    opprettet = opprettet,
    fradrag = FradragFactory.nyFradragsperiode(
        fradragstype = type,
        månedsbeløp = månedsbeløp,
        periode = periode,
        utenlandskInntekt = utenlandskInntekt,
        tilhører = tilhører,
    ),
).getOrFail()

fun avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    begrunnelse: String = "begrunnelsensen",
    fritekst: String? = null,
    tidspunktAvsluttet: Tidspunkt = Tidspunkt.now(fixedClock),
): Pair<Sak, AvsluttetRevurdering> {
    return simulertRevurdering().let { (sak, simulert) ->
        val avsluttet = simulert.avslutt(
            begrunnelse = begrunnelse,
            brevvalg = fritekst?.let { Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst(it) },
            tidspunktAvsluttet = tidspunktAvsluttet,
        ).getOrFail()

        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == avsluttet.id } + avsluttet,
            ),
            avsluttet,
        )
    }
}

/**
 * @param clock Defaulter til 2021-01-01
 * @param periode Defaulter til 11 måneder, fra måneden etter clock.
 */
fun simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
    clock: Clock = tikkendeFixedClock(),
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = LocalDate.now(clock).plusMonths(11).endOfMonth(),
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = Stønadsperiode.create(periode),
        clock = clock,
    ),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
): Pair<Sak, StansAvYtelseRevurdering.SimulertStansAvYtelse> {
    return sakOgVedtakSomKanRevurderes.let { (sak, vedtak) ->
        val gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(periode.fraOgMed, clock).getOrFail()
        val revurdering = StansAvYtelseRevurdering.SimulertStansAvYtelse(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            periode = periode,
            grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
            vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
            tilRevurdering = vedtak.id,
            vedtakSomRevurderesMånedsvis = gjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(),
            saksbehandler = saksbehandler,
            simulering = simulerStans(
                sak = sakOgVedtakSomKanRevurderes.first,
                stans = null,
                stansDato = periode.fraOgMed,
                behandler = saksbehandler,
                clock = clock,
                utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
            ).getOrFail().simulering,
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                begrunnelse = "valid",
            ),
            sakinfo = sak.info(),
        )

        sak.copy(
            // Erstatter den gamle versjonen av samme revurderinger.
            revurderinger = sak.revurderinger.filterNot { it.id == revurdering.id } + revurdering,
        ) to revurdering
    }
}

fun iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
    clock: Clock = tikkendeFixedClock(),
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = LocalDate.now(clock).plusMonths(11).endOfMonth(),
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = Stønadsperiode.create(periode),
        clock = clock,
    ),
    attestering: Attestering = attesteringIverksatt(clock),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
): Pair<Sak, StansAvYtelseRevurdering.IverksattStansAvYtelse> {
    return simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    ).let { (sak, simulert) ->
        val iverksatt = simulert.iverksett(attestering).getOrFail()

        sak.copy(
            // Erstatter den gamle versjonen av samme revurderinger.
            revurderinger = sak.revurderinger.filterNot { it.id == iverksatt.id } + iverksatt,
        ) to iverksatt
    }
}

fun avsluttetStansAvYtelseFraIverksattSøknadsbehandlignsvedtak(
    clock: Clock = tikkendeFixedClock(),
    begrunnelse: String = "begrunnelse for å avslutte stans av ytelse",
    tidspunktAvsluttet: Tidspunkt = Tidspunkt.now(clock),
): Pair<Sak, StansAvYtelseRevurdering.AvsluttetStansAvYtelse> {
    return simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(clock).let { (sak, simulert) ->
        val avsluttet = simulert.avslutt(
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
        ).getOrFail()

        sak.copy(
            // Erstatter den gamle versjonen av samme revurderinger.
            revurderinger = sak.revurderinger.filterNot { it.id == avsluttet.id } + avsluttet,
        ) to avsluttet
    }
}

fun simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
    clock: Clock = tikkendeFixedClock(),
    periodeForStans: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = LocalDate.now(clock).plusMonths(11).endOfMonth(),
    ),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periodeForStans,
        clock = clock,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    ).let { it.first to it.second },
    gjenopptaId: UUID = UUID.randomUUID(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    revurderingsårsak: Revurderingsårsak = Revurderingsårsak.create(
        årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
        begrunnelse = "valid",
    ),
): Pair<Sak, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse> {
    require(sakOgVedtakSomKanRevurderes.first.vedtakListe.last() is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse)

    // TODO jah: Vi bør ikke replikere så mange linjer med produksjonskode i GjenopptaYtelseServiceImpl. Vi bør flytte domenekoden fra nevnte fil og kun beholde sideeffektene i servicen.
    return sakOgVedtakSomKanRevurderes.let { (sak, _) ->
        val sisteVedtakPåTidslinje = sak.vedtakstidslinje().tidslinje.lastOrNull() ?: fail("Fant ingen vedtak")

        if (sisteVedtakPåTidslinje.originaltVedtak !is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse) {
            fail("Siste vedtak er ikke stans")
        }
        val gjeldendeVedtaksdata: GjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
            fraOgMed = sisteVedtakPåTidslinje.periode.fraOgMed,
            clock = clock,
        ).getOrFail()

        val revurdering = GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
            id = gjenopptaId,
            opprettet = Tidspunkt.now(clock),
            periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
            grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
            vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger.tilVilkårsvurderingerRevurdering(),
            tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(sisteVedtakPåTidslinje.periode.fraOgMed)!!.id,
            vedtakSomRevurderesMånedsvis = gjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(),
            saksbehandler = saksbehandler,
            simulering = simulerGjenopptak(
                sak = sak,
                gjenopptak = null,
                behandler = saksbehandler,
                clock = clock,
                utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
            ).getOrFail().simulering,
            revurderingsårsak = revurderingsårsak,
            sakinfo = sak.info(),
        )
        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == revurdering.id } + revurdering,
        ) to revurdering
    }
}

/**
 * @param sakOgVedtakSomKanRevurderes Dersom denne ikke sendes inn vil det opprettes 2 vedtak. Der:
 * 1) søknadbehandlingsvedtaket får clock+0,
 * 2) Stansvedtaket får clock+1
 *
 * [GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse] vil få clock+2
 */
fun iverksattGjenopptakelseAvYtelseFraVedtakStansAvYtelse(
    clock: Clock = tikkendeFixedClock(),
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = LocalDate.now(clock).plusMonths(11).endOfMonth(),
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        clock = clock,
    ).let { it.first to it.second },
    attestering: Attestering = attesteringIverksatt(clock),
): Pair<Sak, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> {
    return simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
        periodeForStans = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
    ).let { (sak, simulert) ->
        val iverksatt = simulert.iverksett(attestering)
            .getOrFail("Feil i oppsett for testdata")
        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == iverksatt.id } + iverksatt,
        ) to iverksatt
    }
}

fun avsluttetGjenopptakelseAvYtelseeFraIverksattSøknadsbehandlignsvedtak(
    begrunnelse: String = "begrunnelse for å avslutte stans av ytelse",
    tidspunktAvsluttet: Tidspunkt = Tidspunkt.now(fixedClock),
): Pair<Sak, GjenopptaYtelseRevurdering.AvsluttetGjenoppta> {
    return simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse().let { (sak, simulert) ->
        val avsluttet = simulert.avslutt(
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
        ).getOrFail()

        sak.copy(
            // Erstatter den gamle versjonen av samme revurderinger.
            revurderinger = sak.revurderinger.filterNot { it.id == avsluttet.id } + avsluttet,
        ) to avsluttet
    }
}
