package no.nav.su.se.bakover

import io.ktor.application.call
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.request.header
import io.ktor.routing.Route
import io.ktor.util.KtorExperimentalAPI

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal fun Route.inntektRoutes(oppslag: InntektOppslag) {
    get<InntektPath> { inntektPath ->
        launchWithContext(call) {
            call.audit("slår opp inntekt for person: ${inntektPath.ident}")
            val resultat = oppslag.inntekt(
                    ident = Fnr(inntektPath.ident),
                    innloggetSaksbehandlerToken = call.request.header(Authorization)!!,
                    fomDato = inntektPath.fomDato,
                    tomDato = inntektPath.tomDato
            )
            call.svar(resultat)
        }
    }
}

@KtorExperimentalLocationsAPI
@Location("/inntekt")
internal data class InntektPath(val ident: String, val fomDato: String, val tomDato: String)
