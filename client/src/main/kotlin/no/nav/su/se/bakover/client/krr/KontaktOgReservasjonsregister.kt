package no.nav.su.se.bakover.client.krr

import arrow.core.Either
import no.nav.su.se.bakover.domain.Fnr

interface KontaktOgReservasjonsregister {
    fun hentKontaktinformasjon(fnr: Fnr): Either<KunneIkkeHenteKontaktinformasjon, Kontaktinformasjon>

    object KunneIkkeHenteKontaktinformasjon
}