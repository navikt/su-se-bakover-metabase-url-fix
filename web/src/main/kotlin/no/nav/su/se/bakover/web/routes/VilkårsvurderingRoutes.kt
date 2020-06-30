package no.nav.su.se.bakover.web.routes

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.patch
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.json
import no.nav.su.se.bakover.web.launchWithContext
import no.nav.su.se.bakover.web.lesParameter
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.svar

internal const val vilkårsvurderingPath = "$behandlingPath/{behandlingId}/vilkarsvurderinger"
internal val mapper = jacksonObjectMapper()

@KtorExperimentalAPI
internal fun Route.vilkårsvurderingRoutes(repo: ObjectRepo) {

    patch(vilkårsvurderingPath) {
        launchWithContext(call) {
            Long.lesParameter(call, "behandlingId").fold(
                left = { call.svar(HttpStatusCode.BadRequest.message(it)) },
                right = { id ->
                    call.audit("Oppdaterer vilkårsvurdering for behandling med id: $id")
                    when (val behandling = repo.hentBehandling(id)) {
                        null -> call.svar(HttpStatusCode.NotFound.message("Fant ikke behandling med id:$id"))
                        else -> {
                            val vilkårsvurderinger: List<Vilkårsvurdering> = mapper.readValue(
                                call.receiveTextUTF8(),
                                mapper.typeFactory.constructCollectionType(
                                    List::class.java,
                                    Vilkårsvurdering::class.java
                                )
                            )
                            behandling.oppdaterVilkårsvurderinger(vilkårsvurderinger)
                            call.svar(HttpStatusCode.OK.json(behandling.toJson()))
                        }
                    }
                }
            )
        }
    }
}
