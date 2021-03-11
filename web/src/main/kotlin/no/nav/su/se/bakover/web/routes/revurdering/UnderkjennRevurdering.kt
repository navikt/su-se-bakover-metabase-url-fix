package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.patch
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeUnderkjenneRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.enumContains
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeAktørId
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withRevurderingId

data class UnderkjennBody(
    val grunn: String,
    val kommentar: String
) {
    fun valid() = enumContains<Attestering.Underkjent.Grunn>(grunn) && kommentar.isNotBlank()
}

@KtorExperimentalAPI
internal fun Route.underkjennRevurdering(
    revurderingService: RevurderingService
) {
    authorize(Brukerrolle.Attestant) {
        patch("$revurderingPath/{revurderingId}/underkjenn") {
            val navIdent = call.suUserContext.navIdent

            call.withRevurderingId { revurderingId ->
                Either.catch { deserialize<UnderkjennBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig behandling-body: ", it)
                        call.svar(HttpStatusCode.BadRequest.message("Ugyldig body"))
                    },
                    ifRight = { body ->
                        if (body.valid()) {
                            revurderingService.underkjenn(
                                revurderingId = revurderingId,
                                attestering = Attestering.Underkjent(
                                    attestant = NavIdentBruker.Attestant(navIdent),
                                    grunn = Attestering.Underkjent.Grunn.valueOf(body.grunn),
                                    kommentar = body.kommentar
                                )
                            ).fold(
                                ifLeft = {
                                    val resultat = when (it) {
                                        KunneIkkeUnderkjenneRevurdering.FantIkkePerson -> HttpStatusCode.InternalServerError.errorJson(
                                            "Fant ikke person",
                                            "fant_ikke_person",
                                        )
                                        KunneIkkeUnderkjenneRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
                                        KunneIkkeUnderkjenneRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> HttpStatusCode.InternalServerError.errorJson(
                                            "Kunne ikke hente navn for saksbehandler eller attestant",
                                            "navneoppslag_feilet",
                                        )
                                        KunneIkkeUnderkjenneRevurdering.KunneIkkeLageBrevutkast -> HttpStatusCode.InternalServerError.errorJson(
                                            "Kunne ikke lage brevutkast",
                                            "kunne_ikke_lage_brevutkast",
                                        )
                                        KunneIkkeUnderkjenneRevurdering.FantIkkeAktørId -> fantIkkeAktørId
                                        KunneIkkeUnderkjenneRevurdering.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
                                        is KunneIkkeUnderkjenneRevurdering.UgyldigTilstand -> ugyldigTilstand(
                                            it.fra,
                                            it.til
                                        )
                                    }
                                    call.svar(resultat)
                                },
                                ifRight = {
                                    call.audit("Underkjente behandling med id: $revurderingId")
                                    call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                                }
                            )
                        } else {
                            call.svar(HttpStatusCode.BadRequest.message("Må angi en begrunnelse"))
                        }
                    }
                )
            }
        }
    }
}
