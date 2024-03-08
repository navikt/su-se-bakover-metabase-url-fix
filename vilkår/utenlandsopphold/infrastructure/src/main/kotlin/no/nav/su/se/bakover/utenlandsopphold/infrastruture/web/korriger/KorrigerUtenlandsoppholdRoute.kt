package no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.korriger

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.utenlandsopphold.application.korriger.KorrigerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.RegistrerteUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.kunneIkkeBekrefteJournalposter
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.overlappendePerioder
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.utdatertSaksversjon
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.withVersjon
import vilkår.utenlandsopphold.domain.korriger.KunneIkkeKorrigereUtenlandsopphold.KunneIkkeBekrefteJournalposter
import vilkår.utenlandsopphold.domain.korriger.KunneIkkeKorrigereUtenlandsopphold.OverlappendePeriode
import vilkår.utenlandsopphold.domain.korriger.KunneIkkeKorrigereUtenlandsopphold.UtdatertSaksversjon

fun Route.korrigerUtenlandsoppholdRoute(
    service: KorrigerUtenlandsoppholdService,
) {
    put("/saker/{sakId}/utenlandsopphold/{versjon}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withVersjon { versjon ->
                    call.withBody<KorrigerUtenlandsoppholdJson> { json ->
                        service.korriger(
                            json.toCommand(
                                sakId = sakId,
                                opprettetAv = call.suUserContext.saksbehandler,
                                correlationId = getOrCreateCorrelationIdFromThreadLocal(),
                                brukerroller = call.suUserContext.roller,
                                korrigererVersjon = versjon,
                            ),
                        ).onRight {
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                        }.onLeft {
                            call.svar(
                                when (it) {
                                    OverlappendePeriode -> overlappendePerioder
                                    UtdatertSaksversjon -> utdatertSaksversjon
                                    is KunneIkkeBekrefteJournalposter -> kunneIkkeBekrefteJournalposter(
                                        it.journalposter,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
