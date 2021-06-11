package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.util.UUID

data class Formuegrunnlag(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    val epsFormue: Verdier?,
    val søkersFormue: Verdier,
    val begrunnelse: String?,
) : Grunnlag(), KanPlasseresPåTidslinje<Formuegrunnlag> {
    data class Verdier(
        val verdiIkkePrimærbolig: Int,
        val verdiEiendommer: Int,
        val verdiKjøretøy: Int,
        val innskudd: Int,
        val verdipapir: Int,
        val pengerSkyldt: Int,
        val kontanter: Int,
        val depositumskonto: Int,
    ) {
        init {
            require(
                verdiIkkePrimærbolig >= 0 &&
                    verdiEiendommer >= 0 &&
                    verdiKjøretøy >= 0 &&
                    innskudd >= 0 &&
                    verdipapir >= 0 &&
                    pengerSkyldt >= 0 &&
                    kontanter >= 0 &&
                    depositumskonto >= 0,
            ) {
                "Alle formueverdiene må være større eller lik 0. Var: $this"
            }
        }

        internal fun sumVerdier(): Int {
            return verdiIkkePrimærbolig +
                verdiEiendommer +
                verdiKjøretøy +
                innskudd +
                verdipapir +
                pengerSkyldt +
                kontanter +
                depositumskonto
        }
    }

    fun oppdaterPeriode(periode: Periode): Formuegrunnlag {
        return this.copy(periode = periode)
    }

    companion object {
        fun tryCreate(
            periode: Periode,
            epsFormue: Verdier?,
            søkersFormue: Verdier,
            begrunnelse: String?,
        ): Either<KunneIkkeLageFormueGrunnlag, Formuegrunnlag> {
            // kanskje sjekke at bosituasjon har eps for hele perioden hvis vi får inn epsformue her

            return Formuegrunnlag(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = Tidspunkt.now(),
                epsFormue = epsFormue,
                søkersFormue = søkersFormue,
                begrunnelse = begrunnelse,
            ).right()
        }
    }

    fun sumFormue(): Int = søkersFormue.sumVerdier() + (epsFormue?.sumVerdier() ?: 0)

    override fun copy(args: CopyArgs.Tidslinje): Formuegrunnlag = when (args) {
        CopyArgs.Tidslinje.Full -> {
            this.copy(id = UUID.randomUUID())
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            this.copy(id = UUID.randomUUID(), periode = args.periode)
        }
    }
}

object KunneIkkeLageFormueGrunnlag
