package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import java.util.UUID

data class Søknad(
    val sakId: UUID,
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val søknadInnhold: SøknadInnhold,
    val søknadTrukket: SøknadTrukket? = null,
)

data class SøknadTrukket(
    val tidspunkt: Tidspunkt,
    val saksbehandler: Saksbehandler
)
