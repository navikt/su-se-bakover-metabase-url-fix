package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn.Companion.toAvslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.inneholderUfullstendigeBosituasjoner
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.avslag.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import java.time.Clock
import java.util.UUID

sealed interface UnderkjentSøknadsbehandling :
    Søknadsbehandling,
    KanOppdaterePeriodeGrunnlagVilkår,
    KanBeregnes {
    abstract override val id: UUID
    abstract override val opprettet: Tidspunkt
    abstract override val sakId: UUID
    abstract override val saksnummer: Saksnummer
    abstract override val søknad: Søknad.Journalført.MedOppgave
    abstract override val oppgaveId: OppgaveId
    abstract override val fnr: Fnr
    abstract override val saksbehandler: NavIdentBruker.Saksbehandler
    abstract override val attesteringer: Attesteringshistorikk
    abstract override val aldersvurdering: Aldersvurdering
    abstract override val avkorting: AvkortingVedSøknadsbehandling.Vurdert

    fun nyOppgaveId(nyOppgaveId: OppgaveId): UnderkjentSøknadsbehandling

    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): Either<KunneIkkeLeggeTilSkattegrunnlag, Søknadsbehandling> =
        when (this.eksterneGrunnlag.skatt) {
            is EksterneGrunnlagSkatt.Hentet -> this.copyInternal(
                grunnlagsdataOgVilkårsvurderinger = this.grunnlagsdataOgVilkårsvurderinger.leggTilSkatt(skatt),
            ).right()

            EksterneGrunnlagSkatt.IkkeHentet -> KunneIkkeLeggeTilSkattegrunnlag.KanIkkeLeggeTilSkattForTilstandUtenAtDenHarBlittHentetFør.left()
        }

    data class Innvilget(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val søknad: Søknad.Journalført.MedOppgave,
        override val oppgaveId: OppgaveId,
        override val fnr: Fnr,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val fritekstTilBrev: String,
        override val aldersvurdering: Aldersvurdering,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        override val avkorting: AvkortingVedSøknadsbehandling.KlarTilIverksetting,
        override val sakstype: Sakstype,
    ) : UnderkjentSøknadsbehandling {
        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode
        override val periode: Periode = aldersvurdering.stønadsperiode.periode

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
        }

        override fun nyOppgaveId(nyOppgaveId: OppgaveId): Innvilget {
            return this.copy(oppgaveId = nyOppgaveId)
        }

        override fun copyInternal(
            stønadsperiode: Stønadsperiode,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
            aldersvurdering: Aldersvurdering,
        ): Innvilget {
            return copy(
                aldersvurdering = aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
            )
        }

        override fun accept(visitor: SøknadsbehandlingVisitor) {
            visitor.visit(this)
        }

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        override fun simuler(
            saksbehandler: NavIdentBruker.Saksbehandler,
            clock: Clock,
            simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>?) -> Either<SimulerUtbetalingFeilet, Simulering>,
        ): Either<KunneIkkeSimulereBehandling, SimulertSøknadsbehandling> {
            return simuler(
                beregning,
                when (sakstype) {
                    Sakstype.ALDER -> {
                        null
                    }

                    Sakstype.UFØRE -> {
                        vilkårsvurderinger.uføreVilkår()
                            .getOrElse { throw IllegalStateException("Søknadsbehandling uføre: $id mangler uføregrunnlag") }.grunnlag.toNonEmptyList()
                    }
                },
            ).mapLeft {
                KunneIkkeSimulereBehandling.KunneIkkeSimulere(it)
            }.map { simulering ->
                SimulertSøknadsbehandling(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    simulering = simulering,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                    attesteringer = attesteringer,
                    søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk.leggTilNyHendelse(
                        saksbehandlingsHendelse = Søknadsbehandlingshendelse(
                            tidspunkt = Tidspunkt.now(clock),
                            saksbehandler = saksbehandler,
                            handling = SøknadsbehandlingsHandling.Simulert,
                        ),
                    ),
                    avkorting = avkorting,
                    sakstype = sakstype,
                    saksbehandler = saksbehandler,
                )
            }
        }

        fun tilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler,
            clock: Clock,
        ): Either<ValideringsfeilAttestering, SøknadsbehandlingTilAttestering.Innvilget> {
            if (grunnlagsdata.bosituasjon.inneholderUfullstendigeBosituasjoner()) {
                return ValideringsfeilAttestering.InneholderUfullstendigBosituasjon.left()
            }
            if (simulering.harFeilutbetalinger()) {
                /**
                 * Kun en nødbrems for tilfeller som i utgangspunktet skal være håndtert og forhindret av andre mekanismer.
                 */
                throw IllegalStateException("Simulering inneholder feilutbetalinger")
            }
            return SøknadsbehandlingTilAttestering.Innvilget(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                beregning = beregning,
                simulering = simulering,
                saksbehandler = saksbehandler,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk.leggTilNyHendelse(
                    saksbehandlingsHendelse = Søknadsbehandlingshendelse(
                        tidspunkt = Tidspunkt.now(clock),
                        saksbehandler = saksbehandler,
                        handling = SøknadsbehandlingsHandling.SendtTilAttestering,
                    ),
                ),
                avkorting = avkorting,
                sakstype = sakstype,
            ).right()
        }
    }

    sealed interface Avslag : UnderkjentSøknadsbehandling, ErAvslag {

        override val avkorting: AvkortingVedSøknadsbehandling.IngenAvkorting get() = AvkortingVedSøknadsbehandling.IngenAvkorting

        data class MedBeregning(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val fnr: Fnr,
            override val beregning: Beregning,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attesteringer: Attesteringshistorikk,
            override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
            override val fritekstTilBrev: String,
            override val aldersvurdering: Aldersvurdering,
            override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            override val sakstype: Sakstype,
        ) : Avslag {
            override val periode: Periode = aldersvurdering.stønadsperiode.periode
            override val simulering: Simulering? = null
            override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode
            private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                    is AvslagGrunnetBeregning.Nei -> emptyList()
                }

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
                grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
            }

            override fun skalSendeVedtaksbrev(): Boolean {
                return true
            }

            override fun copyInternal(
                stønadsperiode: Stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
                søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
                aldersvurdering: Aldersvurdering,
            ): MedBeregning {
                return copy(
                    aldersvurdering = aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                    søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
                )
            }

            override fun nyOppgaveId(nyOppgaveId: OppgaveId): MedBeregning {
                return this.copy(oppgaveId = nyOppgaveId)
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            fun tilAttestering(
                saksbehandler: NavIdentBruker.Saksbehandler,
                clock: Clock,
            ): Either<ValideringsfeilAttestering, SøknadsbehandlingTilAttestering.Avslag.MedBeregning> {
                if (grunnlagsdata.bosituasjon.inneholderUfullstendigeBosituasjoner()) {
                    return ValideringsfeilAttestering.InneholderUfullstendigBosituasjon.left()
                }
                return SøknadsbehandlingTilAttestering.Avslag.MedBeregning(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                    attesteringer = attesteringer,
                    søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk.leggTilNyHendelse(
                        saksbehandlingsHendelse = Søknadsbehandlingshendelse(
                            tidspunkt = Tidspunkt.now(clock),
                            saksbehandler = saksbehandler,
                            handling = SøknadsbehandlingsHandling.SendtTilAttestering,
                        ),
                    ),
                    sakstype = sakstype,
                ).right()
            }

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.vurdering) {
                is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                is Vilkårsvurderingsresultat.Uavklart -> emptyList()
            } + avslagsgrunnForBeregning
        }

        data class UtenBeregning(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val fnr: Fnr,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attesteringer: Attesteringshistorikk,
            override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
            override val fritekstTilBrev: String,
            override val aldersvurdering: Aldersvurdering,
            override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            override val sakstype: Sakstype,
        ) : Avslag {
            override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode
            override val beregning = null
            override val simulering: Simulering? = null

            override fun copyInternal(
                stønadsperiode: Stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
                søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
                aldersvurdering: Aldersvurdering,
            ): UtenBeregning {
                return copy(
                    aldersvurdering = aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                    søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
                )
            }

            override fun skalSendeVedtaksbrev(): Boolean {
                return true
            }

            override val periode: Periode = aldersvurdering.stønadsperiode.periode

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun nyOppgaveId(nyOppgaveId: OppgaveId): UtenBeregning {
                return this.copy(oppgaveId = nyOppgaveId)
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            fun tilAttestering(
                saksbehandler: NavIdentBruker.Saksbehandler,
                clock: Clock,
            ): Either<ValideringsfeilAttestering, SøknadsbehandlingTilAttestering.Avslag.UtenBeregning> {
                if (grunnlagsdata.bosituasjon.inneholderUfullstendigeBosituasjoner()) {
                    return ValideringsfeilAttestering.InneholderUfullstendigBosituasjon.left()
                }
                return SøknadsbehandlingTilAttestering.Avslag.UtenBeregning(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                    attesteringer = attesteringer,
                    søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk.leggTilNyHendelse(
                        saksbehandlingsHendelse = Søknadsbehandlingshendelse(
                            tidspunkt = Tidspunkt.now(clock),
                            saksbehandler = saksbehandler,
                            handling = SøknadsbehandlingsHandling.SendtTilAttestering,
                        ),
                    ),
                    sakstype = sakstype,
                ).right()
            }

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.vurdering) {
                is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                is Vilkårsvurderingsresultat.Uavklart -> emptyList()
            }
        }
    }
}
