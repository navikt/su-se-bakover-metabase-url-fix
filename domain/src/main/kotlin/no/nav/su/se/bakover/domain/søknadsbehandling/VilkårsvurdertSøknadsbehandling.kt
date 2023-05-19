package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.inneholderUfullstendigeBosituasjoner
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.skatt.EksternGrunnlagSkattRequest
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.avslag.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import java.time.Clock
import java.util.UUID

sealed class VilkårsvurdertSøknadsbehandling :
    Søknadsbehandling(),
    Søknadsbehandling.KanOppdaterePeriodeGrunnlagVilkår {

    abstract override val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert

    override fun leggTilSkatt(skatt: EksternGrunnlagSkattRequest): Either<KunneIkkeLeggeTilSkattegrunnlag, Søknadsbehandling> {
        return copyInternal(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTilSkatt(skatt).let {
                if (it !is GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling) throw IllegalStateException("Søknadsbehandling kan kun ha GrunnlagsdataOgVilkårsvurderinger av typen Søkndsbehandling") else it
            },
        ).right()
    }

    companion object {
        fun opprett(
            id: UUID,
            opprettet: Tidspunkt,
            sakId: UUID,
            saksnummer: Saksnummer,
            søknad: Søknad.Journalført.MedOppgave,
            oppgaveId: OppgaveId,
            fnr: Fnr,
            fritekstTilBrev: String,
            aldersvurdering: Aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            attesteringer: Attesteringshistorikk,
            saksbehandlingsHistorikk: Søknadsbehandlingshistorikk,
            avkorting: AvkortingVedSøknadsbehandling.Uhåndtert,
            sakstype: Sakstype,
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): VilkårsvurdertSøknadsbehandling {
            val grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata
            val vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
            val eksterneGrunnlag = grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag
            val oppdaterteVilkårsvurderinger = vilkårsvurderinger.let {
                if (vilkårsvurderinger.opplysningspliktVilkår() !is OpplysningspliktVilkår.Vurdert) {
                    it.leggTil(
                        /**
                         * Legger til implisitt vilkår for oppfylt opplysningsplikt dersom dette ikke er vurdert fra før.
                         * Tar enn så lenge ikke stilling til dette vilkåret fra frontend ved søknadsbehandling.
                         */
                        OpplysningspliktVilkår.Vurdert.tryCreate(
                            vurderingsperioder = nonEmptyListOf(
                                VurderingsperiodeOpplysningsplikt.create(
                                    id = UUID.randomUUID(),
                                    opprettet = opprettet,
                                    periode = aldersvurdering.stønadsperiode.periode,
                                    grunnlag = Opplysningspliktgrunnlag(
                                        id = UUID.randomUUID(),
                                        opprettet = opprettet,
                                        periode = aldersvurdering.stønadsperiode.periode,
                                        beskrivelse = OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon,
                                    ),
                                ),
                            ),
                        ).getOrElse { throw IllegalArgumentException(it.toString()) },
                    )
                } else {
                    it
                }
            }
            return when (oppdaterteVilkårsvurderinger.vurdering) {
                is Vilkårsvurderingsresultat.Avslag -> {
                    Avslag(
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        fritekstTilBrev = fritekstTilBrev,
                        aldersvurdering = aldersvurdering,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = oppdaterteVilkårsvurderinger,
                        eksterneGrunnlag = eksterneGrunnlag,
                        attesteringer = attesteringer,
                        søknadsbehandlingsHistorikk = saksbehandlingsHistorikk,
                        avkorting = avkorting.kanIkke(),
                        sakstype = sakstype,
                        saksbehandler = saksbehandler,
                    )
                }

                is Vilkårsvurderingsresultat.Innvilget -> {
                    Innvilget(
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        fritekstTilBrev = fritekstTilBrev,
                        aldersvurdering = aldersvurdering,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = oppdaterteVilkårsvurderinger,
                        eksterneGrunnlag = eksterneGrunnlag,
                        attesteringer = attesteringer,
                        søknadsbehandlingsHistorikk = saksbehandlingsHistorikk,
                        avkorting = avkorting.uhåndtert(),
                        sakstype = sakstype,
                        saksbehandler = saksbehandler,
                    )
                }

                is Vilkårsvurderingsresultat.Uavklart -> {
                    Uavklart(
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        fritekstTilBrev = fritekstTilBrev,
                        aldersvurdering = aldersvurdering,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = oppdaterteVilkårsvurderinger,
                        eksterneGrunnlag = eksterneGrunnlag,
                        attesteringer = attesteringer,
                        søknadsbehandlingsHistorikk = saksbehandlingsHistorikk,
                        avkorting = avkorting.kanIkke(),
                        sakstype = sakstype,
                        saksbehandler = saksbehandler,
                    )
                }
            }
        }
    }

    data class Innvilget(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val søknad: Søknad.Journalført.MedOppgave,
        override val oppgaveId: OppgaveId,
        override val fnr: Fnr,
        override val fritekstTilBrev: String,
        override val aldersvurdering: Aldersvurdering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
        override val eksterneGrunnlag: EksterneGrunnlag,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : VilkårsvurdertSøknadsbehandling(), KanBeregnes {
        override val periode: Periode = aldersvurdering.stønadsperiode.periode
        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

        override val beregning = null
        override val simulering: Simulering? = null

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            // TODO jah: Enable denne når det ikke finnes proddata med ufullstendig i denne tilstanden:
            // grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
        }

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        override fun accept(visitor: SøknadsbehandlingVisitor) {
            visitor.visit(this)
        }

        override fun copyInternal(
            stønadsperiode: Stønadsperiode,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            avkorting: AvkortingVedSøknadsbehandling,
            søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
            aldersvurdering: Aldersvurdering,
        ): VilkårsvurdertSøknadsbehandling {
            return copy(
                aldersvurdering = aldersvurdering,
                grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                eksterneGrunnlag = grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag,
                søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
            )
        }
    }

    data class Avslag(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val søknad: Søknad.Journalført.MedOppgave,
        override val oppgaveId: OppgaveId,
        override val fnr: Fnr,
        override val fritekstTilBrev: String,
        override val aldersvurdering: Aldersvurdering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
        override val eksterneGrunnlag: EksterneGrunnlag,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : VilkårsvurdertSøknadsbehandling(), ErAvslag {
        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode
        override val beregning = null
        override val simulering: Simulering? = null

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        override fun copyInternal(
            stønadsperiode: Stønadsperiode,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            avkorting: AvkortingVedSøknadsbehandling,
            søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
            aldersvurdering: Aldersvurdering,
        ): Avslag {
            return copy(
                aldersvurdering = aldersvurdering,
                grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                eksterneGrunnlag = grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag,
                søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
            )
        }

        override val periode: Periode = aldersvurdering.stønadsperiode.periode

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
        }

        override fun accept(visitor: SøknadsbehandlingVisitor) {
            visitor.visit(this)
        }

        /**
         * Til bruk der systemet har behov for å gjøre handling
         * Se eksempel: [AvslåPgaManglendeDokumentasjon.kt]
         */
        fun tilAttestering(
            fritekstTilBrev: String,
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
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                eksterneGrunnlag = eksterneGrunnlag,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = this.søknadsbehandlingsHistorikk,
                avkorting = avkorting.håndter().kanIkke(),
                sakstype = sakstype,
            ).right()
        }

        fun tilAttesteringForSaksbehandler(
            saksbehandler: NavIdentBruker.Saksbehandler,
            fritekstTilBrev: String,
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
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                eksterneGrunnlag = eksterneGrunnlag,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk.leggTilNyHendelse(
                    saksbehandlingsHendelse = Søknadsbehandlingshendelse(
                        tidspunkt = Tidspunkt.now(clock),
                        saksbehandler = saksbehandler,
                        handling = SøknadsbehandlingsHandling.SendtTilAttestering,
                    ),
                ),
                avkorting = avkorting.håndter().kanIkke(),
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

    data class Uavklart(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val søknad: Søknad.Journalført.MedOppgave,
        override val oppgaveId: OppgaveId,
        override val fnr: Fnr,
        override val fritekstTilBrev: String,
        override val aldersvurdering: Aldersvurdering?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
        override val eksterneGrunnlag: EksterneGrunnlag,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : VilkårsvurdertSøknadsbehandling() {
        override val stønadsperiode: Stønadsperiode? = aldersvurdering?.stønadsperiode
        override val beregning = null
        override val simulering: Simulering? = null

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        override fun copyInternal(
            stønadsperiode: Stønadsperiode,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            avkorting: AvkortingVedSøknadsbehandling,
            søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
            aldersvurdering: Aldersvurdering,
        ): Uavklart {
            return copy(
                aldersvurdering = aldersvurdering,
                grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                eksterneGrunnlag = grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag,
                søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
            )
        }

        override val periode: Periode
            get() = stønadsperiode?.periode ?: throw StønadsperiodeIkkeDefinertException(id)

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
        }

        override fun accept(visitor: SøknadsbehandlingVisitor) {
            visitor.visit(this)
        }

        data class StønadsperiodeIkkeDefinertException(
            val id: UUID,
        ) : RuntimeException("Sønadsperiode er ikke definert for søknadsbehandling:$id")
    }
}
