package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.extensions.førsteINesteMåned
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.TidslinjeForUtbetalinger
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingslinjePåTidslinje

object KryssjekkTidslinjerOgSimulering {
    fun sjekk(
        underArbeidEndringsperiode: Periode,
        underArbeid: Utbetaling.UtbetalingForSimulering,
        eksisterende: Utbetalinger,
        simuler: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    ): Either<KryssjekkAvTidslinjeOgSimuleringFeilet, Unit> {
        val periode = Periode.create(
            fraOgMed = underArbeid.tidligsteDato(),
            tilOgMed = underArbeid.senesteDato(),
        )
        val simulertUtbetaling = simuler(underArbeid, periode)
            .getOrElse {
                log.error("Feil ved kryssjekk av tidslinje og simulering, kunne ikke simulere: $it")
                return KryssjekkAvTidslinjeOgSimuleringFeilet.KunneIkkeSimulere(it).left()
            }

        val tidslinjeEksisterendeOgUnderArbeid = (eksisterende + underArbeid)
            .tidslinje()
            .getOrElse {
                log.error("Feil ved kryssjekk av tidslinje og simulering, kunne ikke generere tidslinjer: $it")
                return KryssjekkAvTidslinjeOgSimuleringFeilet.KunneIkkeGenerereTidslinje.left()
            }

        sjekkTidslinjeMotSimulering(
            tidslinjeEksisterendeOgUnderArbeid = tidslinjeEksisterendeOgUnderArbeid,
            simulering = simulertUtbetaling.simulering,
        ).getOrElse {
            log.error("Feil (${it.map { it::class.simpleName }}) ved kryssjekk av tidslinje og simulering. Se sikkerlogg for detaljer")
            sikkerLogg.error("Feil: $it ved kryssjekk av tidslinje: $tidslinjeEksisterendeOgUnderArbeid og simulering: ${simulertUtbetaling.simulering}")
            return KryssjekkAvTidslinjeOgSimuleringFeilet.KryssjekkFeilet(it.first()).left()
        }

        if (eksisterende.harUtbetalingerEtterDato(underArbeidEndringsperiode.tilOgMed)) {
            val rekonstruertPeriode = Periode.create(
                fraOgMed = underArbeidEndringsperiode.tilOgMed.førsteINesteMåned(),
                tilOgMed = eksisterende.maxOf { it.senesteDato() },
            )
            val tidslinjeUnderArbeid = underArbeid.tidslinje()

            val tidslinjeEksisterende = eksisterende.tidslinje().getOrElse {
                log.error("Feil ved kryssjekk av tidslinje og simulering, kunne ikke generere tidslinjer: $it")
                return KryssjekkAvTidslinjeOgSimuleringFeilet.KunneIkkeGenerereTidslinje.left()
            }
            if (!tidslinjeUnderArbeid.ekvivalentMedInnenforPeriode(tidslinjeEksisterende, rekonstruertPeriode)) {
                log.error("Feil ved kryssjekk av tidslinje og simulering. Tidslinje for ny utbetaling er ulik eksisterende. Se sikkerlogg for detaljer")
                sikkerLogg.error("Feil ved kryssjekk av tidslinje: Tidslinje for ny utbetaling:$tidslinjeUnderArbeid er ulik eksisterende:$tidslinjeEksisterende for rekonstruert periode:$rekonstruertPeriode")
                return KryssjekkAvTidslinjeOgSimuleringFeilet.RekonstruertUtbetalingsperiodeErUlikOpprinnelig.left()
            }
        }
        return Unit.right()
    }
}

sealed interface KryssjekkAvTidslinjeOgSimuleringFeilet {
    data class KryssjekkFeilet(val feil: KryssjekkFeil) : KryssjekkAvTidslinjeOgSimuleringFeilet
    object RekonstruertUtbetalingsperiodeErUlikOpprinnelig : KryssjekkAvTidslinjeOgSimuleringFeilet

    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : KryssjekkAvTidslinjeOgSimuleringFeilet

    object KunneIkkeGenerereTidslinje : KryssjekkAvTidslinjeOgSimuleringFeilet
}

sealed class KryssjekkFeil(val prioritet: Int) : Comparable<KryssjekkFeil> {
    data class StansMedFeilutbetaling(val måned: Måned) : KryssjekkFeil(prioritet = 1)
    data class GjenopptakMedFeilutbetaling(val måned: Måned) : KryssjekkFeil(prioritet = 1)
    data class KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig(
        val periode: Måned,
        val simulertType: String,
        val tidslinjeType: String,
    ) : KryssjekkFeil(prioritet = 2)

    data class SimulertBeløpOgTidslinjeBeløpErForskjellig(
        val periode: Måned,
        val simulertBeløp: Int,
        val tidslinjeBeløp: Int,
    ) : KryssjekkFeil(prioritet = 2)

    override fun compareTo(other: KryssjekkFeil): Int {
        return this.prioritet.compareTo(other.prioritet)
    }
}

private fun sjekkTidslinjeMotSimulering(
    tidslinjeEksisterendeOgUnderArbeid: TidslinjeForUtbetalinger,
    simulering: Simulering,
): Either<List<KryssjekkFeil>, Unit> {
    val feil = mutableListOf<KryssjekkFeil>()

    if (simulering.erAlleMånederUtenUtbetaling()) {
        simulering.periode().also { periode ->
            periode.måneder().forEach {
                val utbetaling = tidslinjeEksisterendeOgUnderArbeid.gjeldendeForDato(it.fraOgMed)!!
                if (!(
                        utbetaling is UtbetalingslinjePåTidslinje.Stans ||
                            utbetaling is UtbetalingslinjePåTidslinje.Opphør ||
                            (utbetaling is UtbetalingslinjePåTidslinje.Ny && utbetaling.beløp == 0) ||
                            (utbetaling is UtbetalingslinjePåTidslinje.Reaktivering && utbetaling.beløp == 0)
                        )
                ) {
                    feil.add(
                        KryssjekkFeil.KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig(
                            periode = it,
                            simulertType = "IngenUtbetaling",
                            tidslinjeType = utbetaling::class.toString(),
                        ),
                    )
                }
            }
        }
    } else {
        simulering.månederMedSimuleringsresultat().forEach { måned ->
            val utbetaling = tidslinjeEksisterendeOgUnderArbeid.gjeldendeForMåned(måned)!!
            if (utbetaling is UtbetalingslinjePåTidslinje.Stans && simulering.harFeilutbetalinger()) {
                feil.add(KryssjekkFeil.StansMedFeilutbetaling(måned))
            }
        }

        simulering.månederMedSimuleringsresultat().forEach { måned ->
            val utbetaling = tidslinjeEksisterendeOgUnderArbeid.gjeldendeForMåned(måned)!!
            if (utbetaling is UtbetalingslinjePåTidslinje.Reaktivering && simulering.harFeilutbetalinger()) {
                feil.add(KryssjekkFeil.GjenopptakMedFeilutbetaling(måned))
            }
        }

        simulering.hentTotalUtbetaling().forEach { månedsbeløp ->
            kryssjekkBeløp(
                tolketPeriode = månedsbeløp.periode,
                simulertUtbetaling = månedsbeløp.beløp.sum(),
                beløpPåTidslinje = tidslinjeEksisterendeOgUnderArbeid.gjeldendeForDato(månedsbeløp.periode.fraOgMed)!!.beløp,
            ).getOrElse { feil.add(it) }
        }
    }
    return when (feil.isEmpty()) {
        true -> Unit.right()
        false -> feil.sorted().left()
    }
}

private fun kryssjekkBeløp(
    tolketPeriode: Måned,
    simulertUtbetaling: Int,
    beløpPåTidslinje: Int,
): Either<KryssjekkFeil.SimulertBeløpOgTidslinjeBeløpErForskjellig, Unit> {
    return if (simulertUtbetaling != beløpPåTidslinje) {
        KryssjekkFeil.SimulertBeløpOgTidslinjeBeløpErForskjellig(
            periode = tolketPeriode,
            simulertBeløp = simulertUtbetaling,
            tidslinjeBeløp = beløpPåTidslinje,
        ).left()
    } else {
        Unit.right()
    }
}
