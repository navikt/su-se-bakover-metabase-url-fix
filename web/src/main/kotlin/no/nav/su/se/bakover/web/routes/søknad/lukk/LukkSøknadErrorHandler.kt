package no.nav.su.se.bakover.web.routes.søknad.lukk

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.FantIkkePerson
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.FantIkkeSøknad
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.SøknadErAlleredeLukket
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.SøknadHarEnBehandling
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.UgyldigTrukketDato
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.message

internal object LukkSøknadErrorHandler {
    fun kunneIkkeLukkeSøknadResponse(request: LukkSøknadRequest, error: KunneIkkeLukkeSøknad): Resultat {
        val søknadId = request.søknadId
        return when (error) {
            is SøknadErAlleredeLukket -> BadRequest.message("Søknad med id $søknadId er allerede trukket")
            is SøknadHarEnBehandling -> BadRequest.message("Søknad med id $søknadId har en aktiv behandling og kan derfor ikke lukkes")
            is FantIkkeSøknad -> NotFound.message("Fant ikke søknad med id $søknadId")
            is UgyldigTrukketDato -> BadRequest.message("Ugyldig lukket dato. Dato må være etter opprettet og kan ikke være frem i tid")
            is FantIkkePerson -> NotFound.message("Fant ikke person")
            is KunneIkkeLukkeSøknad.SøknadManglerOppgave -> InternalServerError.message("Søknad med id $søknadId mangler oppgave")
            KunneIkkeLukkeSøknad.KunneIkkeGenerereDokument -> InternalServerError.errorJson(
                "Feil ved generering av dokument",
                "feil_ved_generering_av_dokument",
            )
        }
    }
}
