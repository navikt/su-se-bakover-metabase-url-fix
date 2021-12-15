package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.time.Clock

abstract class Statusovergang<L, T> : StatusovergangVisitor {

    protected lateinit var result: Either<L, T>
    fun get(): Either<L, T> = result

    class TilVilkårsvurdert(
        private val behandlingsinformasjon: Behandlingsinformasjon,
        private val clock: Clock,
    ) : Statusovergang<Nothing, Søknadsbehandling.Vilkårsvurdert>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
            result = søknadsbehandling.tilVilkårsvurdert(behandlingsinformasjon, clock = clock).right()
        }
    }

    class TilSimulert(
        private val simulering: (beregning: Beregning) -> Either<SimuleringFeilet, Simulering>,
    ) : Statusovergang<SimuleringFeilet, Søknadsbehandling.Simulert>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
            simulering(søknadsbehandling.beregning)
                .mapLeft { result = it.left() }
                .map { result = søknadsbehandling.tilSimulert(it).right() }
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            simulering(søknadsbehandling.beregning)
                .mapLeft { result = it.left() }
                .map { result = søknadsbehandling.tilSimulert(it).right() }
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            simulering(søknadsbehandling.beregning)
                .mapLeft { result = it.left() }
                .map { result = søknadsbehandling.tilSimulert(it).right() }
        }
    }

    class TilAttestering(
        private val saksbehandler: NavIdentBruker.Saksbehandler,
        private val fritekstTilBrev: String,
    ) : Statusovergang<Nothing, Søknadsbehandling.TilAttestering>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            result = søknadsbehandling.tilAttestering(saksbehandler, fritekstTilBrev).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
            result = søknadsbehandling.tilAttestering(saksbehandler).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
            result = søknadsbehandling.tilAttestering(saksbehandler).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            result = søknadsbehandling.tilAttestering(saksbehandler).right()
        }
    }

    class TilUnderkjent(
        private val attestering: Attestering,
    ) : Statusovergang<SaksbehandlerOgAttestantKanIkkeVæreSammePerson, Søknadsbehandling.Underkjent>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
            evaluerStatusovergang(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
            evaluerStatusovergang(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
            evaluerStatusovergang(søknadsbehandling)
        }

        private fun evaluerStatusovergang(søknadsbehandling: Søknadsbehandling.TilAttestering) {
            result = when (saksbehandlerOgAttestantErForskjellig(søknadsbehandling, attestering)) {
                true -> søknadsbehandling.tilUnderkjent(attestering).right()
                false -> SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
            }
        }

        private fun saksbehandlerOgAttestantErForskjellig(
            søknadsbehandling: Søknadsbehandling.TilAttestering,
            attestering: Attestering,
        ): Boolean = søknadsbehandling.saksbehandler.navIdent != attestering.attestant.navIdent
    }

    object SaksbehandlerOgAttestantKanIkkeVæreSammePerson

    class TilIverksatt(
        private val attestering: Attestering,
        private val innvilget: (søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) -> Either<KunneIkkeIverksette, UUID30>,
    ) : Statusovergang<KunneIkkeIverksette, Søknadsbehandling.Iverksatt>() {

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
            result = if (saksbehandlerOgAttestantErForskjellig(søknadsbehandling, attestering)) {
                søknadsbehandling.tilIverksatt(attestering).right()
            } else {
                KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
            result = if (saksbehandlerOgAttestantErForskjellig(søknadsbehandling, attestering)) {
                søknadsbehandling.tilIverksatt(attestering).right()
            } else {
                KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
            result = if (saksbehandlerOgAttestantErForskjellig(søknadsbehandling, attestering)) {

                /**
                 * Skulle ideelt gjort dette inne i [Søknadsbehandling.TilAttestering.Innvilget.tilIverksatt], men må få
                 * sjekket dette før vi oversender til oppdrag.
                 * //TODO erstatt statusovergang med funksjon
                 */
                when (søknadsbehandling.avkorting) {
                    Avkortingsvarsel.Ingen -> {
                        // noop
                    }
                    is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> {
                        if (!søknadsbehandling.avkorting.fullstendigAvkortetAv(søknadsbehandling.beregning)) {
                            result = KunneIkkeIverksette.AvkortingErUfullstendig.left()
                            return
                        }
                    }
                    else -> {
                        throw IllegalStateException("Avkorting for søknadsbehandling:${søknadsbehandling.id} er i ugyldig tilstand:${søknadsbehandling.avkorting} for å kunne iverksettes")
                    }
                }

                innvilget(søknadsbehandling)
                    .mapLeft { it }
                    .map { søknadsbehandling.tilIverksatt(attestering) }
            } else {
                KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
        }

        private fun saksbehandlerOgAttestantErForskjellig(
            søknadsbehandling: Søknadsbehandling.TilAttestering,
            attestering: Attestering,
        ): Boolean = søknadsbehandling.saksbehandler.navIdent != attestering.attestant.navIdent
    }

    class OppdaterStønadsperiode(
        private val oppdatertStønadsperiode: Stønadsperiode,
        private val sak: Sak,
        private val clock: Clock,
    ) : Statusovergang<OppdaterStønadsperiode.KunneIkkeOppdatereStønadsperiode, Søknadsbehandling.Vilkårsvurdert>() {

        sealed class KunneIkkeOppdatereStønadsperiode {
            data class KunneIkkeOppdatereGrunnlagsdata(val feil: KunneIkkeLageGrunnlagsdata) :
                KunneIkkeOppdatereStønadsperiode()

            object StønadsperiodeOverlapperMedLøpendeStønadsperiode : KunneIkkeOppdatereStønadsperiode()
            object StønadsperiodeForSenerePeriodeEksisterer : KunneIkkeOppdatereStønadsperiode()
        }

        private fun oppdater(søknadsbehandling: Søknadsbehandling): Either<KunneIkkeOppdatereStønadsperiode, Søknadsbehandling.Vilkårsvurdert> {
            sak.hentPerioderMedLøpendeYtelse().let { stønadsperioder ->
                if (stønadsperioder.any { it overlapper oppdatertStønadsperiode.periode }) {
                    return KunneIkkeOppdatereStønadsperiode.StønadsperiodeOverlapperMedLøpendeStønadsperiode.left()
                }
                if (stønadsperioder.any { it.starterSamtidigEllerSenere(oppdatertStønadsperiode.periode) }) {
                    return KunneIkkeOppdatereStønadsperiode.StønadsperiodeForSenerePeriodeEksisterer.left()
                }
            }
            return Søknadsbehandling.Vilkårsvurdert.Uavklart(
                id = søknadsbehandling.id,
                opprettet = søknadsbehandling.opprettet,
                sakId = søknadsbehandling.sakId,
                saksnummer = søknadsbehandling.saksnummer,
                søknad = søknadsbehandling.søknad,
                oppgaveId = søknadsbehandling.oppgaveId,
                behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                fnr = søknadsbehandling.fnr,
                fritekstTilBrev = søknadsbehandling.fritekstTilBrev,
                stønadsperiode = oppdatertStønadsperiode,
                grunnlagsdata = søknadsbehandling.grunnlagsdata.oppdaterGrunnlagsperioder(
                    oppdatertPeriode = oppdatertStønadsperiode.periode,
                ).getOrHandle { return KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it).left() },
                vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger.oppdaterStønadsperiode(oppdatertStønadsperiode),
                attesteringer = søknadsbehandling.attesteringer,
                avkorting = søknadsbehandling.avkorting,
            ).tilVilkårsvurdert(søknadsbehandling.behandlingsinformasjon, clock = clock).right()
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) {
            result = oppdater(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
            result = oppdater(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
            result = oppdater(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
            result = oppdater(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
            result = oppdater(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
            result = oppdater(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
            result = oppdater(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
            result = oppdater(søknadsbehandling)
        }

        override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
            result = oppdater(søknadsbehandling)
        }
    }
}

fun <T> statusovergang(
    søknadsbehandling: Søknadsbehandling,
    statusovergang: Statusovergang<Nothing, T>,
): T {
    // Kan aldri være Either.Left<Nothing>
    return forsøkStatusovergang(søknadsbehandling, statusovergang).orNull()!!
}

fun <L, T> forsøkStatusovergang(
    søknadsbehandling: Søknadsbehandling,
    statusovergang: Statusovergang<L, T>,
): Either<L, T> {
    søknadsbehandling.accept(statusovergang)
    return statusovergang.get()
}
