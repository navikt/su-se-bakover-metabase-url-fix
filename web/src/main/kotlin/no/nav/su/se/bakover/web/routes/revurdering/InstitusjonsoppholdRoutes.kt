package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.domain.revurdering.RevurderingService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.LeggTilVurderingsperiodeInstitusjonsoppholdJson
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.tilResultat
import java.time.Clock

internal fun Route.institusjonsoppholdRoutes(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
    clock: Clock,
) {
    post("$revurderingPath/{revurderingId}/institusjonsopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<LeggTilVurderingsperiodeInstitusjonsoppholdJson> { body ->
                    call.svar(
                        body.toDomain(clock).map { vilkår ->
                            revurderingService.leggTilInstitusjonsoppholdVilkår(
                                request = LeggTilInstitusjonsoppholdVilkårRequest(
                                    behandlingId = revurderingId,
                                    vilkår = vilkår,
                                ),
                            ).fold(
                                { it.tilResultat() },
                                {
                                    call.audit(it.revurdering.fnr, AuditLogEvent.Action.UPDATE, it.revurdering.id)
                                    Resultat.json(HttpStatusCode.Created, it.json(satsFactory))
                                },
                            )
                        }.getOrElse { it },
                    )
                }
            }
        }
    }
}
