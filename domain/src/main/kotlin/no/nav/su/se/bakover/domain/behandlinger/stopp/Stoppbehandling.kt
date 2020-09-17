package no.nav.su.se.bakover.domain.behandlinger.stopp

import arrow.core.Either
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.behandlinger.stopp.Stoppbehandling.StoppbehandlingPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import java.time.Instant
import java.util.UUID

/**
 * Behandling for midlertidig stopp av ytelsen(e). Det er mulig å starte den igjen etter dette.
 * Den første versjonen stopper alle aktive utbetalinger (de som ikke er utbetalt enda).
 */
sealed class Stoppbehandling : PersistentDomainObject<StoppbehandlingPersistenceObserver>() {
    abstract val id: UUID
    abstract val opprettet: Instant
    abstract val sakId: UUID
    abstract val status: String
    abstract val utbetaling: Utbetaling
    abstract val stoppÅrsak: String
    abstract val saksbehandler: Saksbehandler

    data class Simulert(
        override val id: UUID,
        override val opprettet: Instant,
        override val sakId: UUID,
        override val utbetaling: Utbetaling,
        override val stoppÅrsak: String,
        override val saksbehandler: Saksbehandler
    ) : Stoppbehandling() {

        companion object {
            const val STATUS = "SIMULERT"
        }

        override val status = STATUS

        fun simuler(simuleringClient: SimuleringClient): Either<SimuleringFeilet, Simulert> {
            throw NotImplementedError("$simuleringClient")
        }

        fun sendTilAttestering(
            aktørId: AktørId,
            oppgave: OppgaveClient
        ): Either<KunneIkkeOppretteOppgave, TilAttestering> {
            throw NotImplementedError("$aktørId $oppgave")
        }
    }

    data class TilAttestering(
        override val id: UUID,
        override val opprettet: Instant,
        override val sakId: UUID,
        override val utbetaling: Utbetaling,
        override val stoppÅrsak: String,
        override val saksbehandler: Saksbehandler
    ) : Stoppbehandling() {

        companion object {
            const val STATUS = "TIL_ATTESTERING"
        }

        override val status = STATUS

        fun iverksett(
            publisher: UtbetalingPublisher
        ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Iverksatt> {
            throw NotImplementedError("$publisher")
        }
    }

    data class Iverksatt(
        override val id: UUID,
        override val opprettet: Instant,
        override val sakId: UUID,
        override val utbetaling: Utbetaling,
        override val stoppÅrsak: String,
        override val saksbehandler: Saksbehandler,
        val attestant: Attestant
    ) : Stoppbehandling() {

        companion object {
            const val STATUS = "IVERKSATT"
        }

        override val status = STATUS
    }

    interface StoppbehandlingPersistenceObserver : PersistenceObserver {
        // fun hentOppdrag(sakId: UUID): Oppdrag
        // fun hentFnr(sakId: UUID): Fnr
        // fun attester(behandlingId: UUID, attestant: Attestant): Attestant
    }
}
