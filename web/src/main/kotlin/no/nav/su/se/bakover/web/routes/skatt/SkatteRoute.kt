package no.nav.su.se.bakover.web.routes.skatt

import arrow.core.Either
import arrow.core.flatMap
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.parameter
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.toggle.domain.ToggleClient
import no.nav.su.se.bakover.domain.skatt.SkatteoppslagFeil
import no.nav.su.se.bakover.service.skatt.KunneIkkeHenteSkattemelding
import no.nav.su.se.bakover.service.skatt.SkatteService
import no.nav.su.se.bakover.web.features.authorize
import java.util.UUID

internal const val skattPath = "/skatt"

internal fun Route.skattRoutes(skatteService: SkatteService, toggleService: ToggleClient) {
    get("$skattPath/{behandlingId}") {
        if (!toggleService.isEnabled("supstonad.skattemelding")) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        authorize(Brukerrolle.Saksbehandler) {
            call.parameter("behandlingId")
                .flatMap {
                    Either.catch { UUID.fromString(it) }
                        //TODO: Riktig feilmelding
                        .mapLeft { Feilresponser.fantIkkeBehandling }
                }
                .map { id ->
                    skatteService.hentSamletSkattegrunnlagForBehandling(id)
                        .fold(
                            ifLeft = {
                                //TODO: trenger Fnr for audit
                                //call.audit(id, AuditLogEvent.Action.SEARCH, id)
                                val feilmelding = when (it) {
                                    is KunneIkkeHenteSkattemelding.KallFeilet -> {
                                        when (it.feil) {
                                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr -> HttpStatusCode.NotFound.errorJson(
                                                "Ingen summert skattegrunnlag funnet på oppgitt fødselsnummer og inntektsår",
                                                "inget_skattegrunnlag_for_gitt_fnr_og_år",
                                            )

                                            SkatteoppslagFeil.ManglerRettigheter -> HttpStatusCode.NotFound.errorJson(
                                                "Autentisering eller autoriseringsfeil mot Sigrun/Skatteetaten. Mangler bruker noen rettigheter?",
                                                "mangler_rettigheter_mot_skatt",
                                            )

                                            is SkatteoppslagFeil.Nettverksfeil -> HttpStatusCode.NotFound.errorJson(
                                                "Får ikke kontakt med Sigrun/Skatteetaten. Prøv igjen senere.",
                                                "nettverksfeil_skatt",
                                            )

                                            is SkatteoppslagFeil.UkjentFeil -> HttpStatusCode.NotFound.errorJson(
                                                "Uforventet feil oppstod ved kall til Sigrun/Skatteetaten. Prøv igjen senere.",
                                                "uforventet_feil_mot_skatt",
                                            )
                                        }
                                    }
                                }
                                call.svar(feilmelding)
                            },
                            ifRight = {
                                //TODO: trenger fnr for audit
                                //call.audit(id, AuditLogEvent.Action.ACCESS, null)
                                call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJSON())))
                            },
                        )
                }
        }
    }
}
