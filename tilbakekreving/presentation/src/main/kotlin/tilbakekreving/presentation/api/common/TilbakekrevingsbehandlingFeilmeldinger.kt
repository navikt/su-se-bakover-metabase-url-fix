package tilbakekreving.presentation.api.common

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.errorJson

internal val ingenÅpneKravgrunnlag = HttpStatusCode.BadRequest.errorJson(
    "Ingen ferdig behandlede kravgrunnlag",
    "ingen_ferdig_behandlede_kravgrunnlag",
)

internal val manglerBrukkerroller = HttpStatusCode.InternalServerError.errorJson(
    message = "teknisk feil: Brukeren mangler brukerroller",
    code = "mangler_brukerroller",
)

// TODO jah: flytt til person infra/presentation
internal val ikkeTilgangTilSak = HttpStatusCode.Forbidden.errorJson(
    "Ikke tilgang til sak",
    "ikke_tilgang_til_sak",
)