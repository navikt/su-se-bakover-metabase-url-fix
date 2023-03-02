package no.nav.su.se.bakover.common.infrastructure.web

import arrow.core.Either
import com.auth0.jwt.interfaces.Payload
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.Principal
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.header
import io.ktor.server.request.receiveStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.audit.infrastructure.CefAuditLogger
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.sikkerLogg
import java.util.UUID

fun ApplicationCall.sikkerlogg(msg: String) {
    sikkerLogg.info("${suUserContext.navIdent} $msg")
}

/**
 * Logg til audit.nais (som går videre til ArcSight)
 * @see CefAuditLogger
 */
fun ApplicationCall.audit(
    berørtBruker: Fnr,
    action: AuditLogEvent.Action,
    behandlingId: UUID?,
) {
    CefAuditLogger.log(
        AuditLogEvent(
            navIdent = suUserContext.navIdent,
            berørtBrukerId = berørtBruker,
            action = action,
            behandlingId = behandlingId,
            callId = this.callId,
        ),
    )
}

fun getGroupsFromJWT(applicationConfig: ApplicationConfig, principal: JWTPrincipal): List<String> {
    return getGroupsFromJWT(applicationConfig, principal.payload)
}

fun getGroupsFromJWT(applicationConfig: ApplicationConfig, credential: JWTCredential): List<String> {
    return getGroupsFromJWT(applicationConfig, credential.payload)
}

/**
 * Token som genereres lokalt (av navikt/oauth2-mock-server) vil ikke inneholde gruppene, så vi legger dem på her
 */
private fun getGroupsFromJWT(applicationConfig: ApplicationConfig, payload: Payload): List<String> {
    return if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local) {
        applicationConfig.azure.groups.let {
            listOf(
                it.veileder,
                it.saksbehandler,
                it.attestant,
                it.drift,
            )
        }
    } else {
        payload.getClaim("groups").asList(String::class.java)
    }
}

fun getNAVidentFromJwt(applicationConfig: ApplicationConfig, principal: Principal?): String {
    return if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local) {
        "Z9999999"
    } else {
        (principal as JWTPrincipal).payload.getClaim("NAVident").asString()
    }
}

fun getNavnFromJwt(applicationConfig: ApplicationConfig, principal: Principal?): String {
    return if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local) {
        "Ulrik Utvikler"
    } else {
        (principal as JWTPrincipal).payload.getClaim("name").asString()
    }
}

fun String.toUUID(): Either<String, UUID> {
    return Either.catch { UUID.fromString(this@toUUID) }
        .mapLeft { "${this@toUUID} er ikke en gyldig UUID" }
}

fun ApplicationCall.lesUUID(param: String): Either<String, UUID> {
    return this.parameters[param]?.let {
        it.toUUID().mapLeft { "$param er ikke en gyldig UUID" }
    } ?: Either.Left("$param er ikke et parameter")
}

fun ApplicationCall.lesLong(param: String): Either<String, Long> {
    return this.parameters[param]?.let {
        Either.catch { it.toLong() }.mapLeft { "$param er ikke en gyldig long" }
    } ?: Either.Left("$param er ikke et parameter")
}

fun ApplicationCall.parameter(parameterName: String): Either<Resultat, String> {
    return this.parameters[parameterName]?.let { Either.Right(it) }
        ?: Either.Left(
            HttpStatusCode.BadRequest.errorJson(
                message = "$parameterName er ikke et parameter",
                code = "parameter_mangler",
            ),
        )
}

fun ApplicationCall.authHeader(): String {
    return this.request.header(HttpHeaders.Authorization).toString()
}

suspend inline fun <reified T> deserialize(call: ApplicationCall): T {
    return deserialize(call.receiveTextUTF8())
}

suspend inline fun ApplicationCall.receiveTextUTF8(): String {
    return withContext(Dispatchers.IO) {
        String(receiveStream().readBytes())
    }
}

suspend inline fun ApplicationCall.withStringParam(parameterName: String, ifRight: (String) -> Unit) {
    this.parameter(parameterName).fold(
        ifLeft = { this.svar(it) },
        ifRight = { ifRight(it) },
    )
}

suspend inline fun ApplicationCall.withSakId(ifRight: (UUID) -> Unit) {
    this.lesUUID("sakId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "sakId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend inline fun ApplicationCall.withRevurderingId(ifRight: (UUID) -> Unit) {
    this.lesUUID("revurderingId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "revurderingId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend inline fun ApplicationCall.withSøknadId(ifRight: (UUID) -> Unit) {
    this.lesUUID("søknadId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "søknadId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend inline fun ApplicationCall.withBehandlingId(ifRight: (UUID) -> Unit) {
    this.lesUUID("behandlingId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "behandlingId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend inline fun ApplicationCall.withVedtakId(ifRight: (UUID) -> Unit) {
    this.lesUUID("vedtakId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "vedtakId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend inline fun ApplicationCall.withKlageId(ifRight: (UUID) -> Unit) {
    this.lesUUID("klageId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "klageId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend inline fun ApplicationCall.withReguleringId(ifRight: (UUID) -> Unit) {
    this.lesUUID("reguleringId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "reguleringId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend inline fun ApplicationCall.withDokumentId(ifRight: (UUID) -> Unit) {
    this.lesUUID("dokumentId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "dokumentId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend inline fun <reified T> ApplicationCall.withBody(ifRight: (T) -> Unit) {
    Either.catch { this.receiveTextUTF8() }
        .onLeft {
            log.error("Feil ved transformering av json-body til UTF-8 inn mot web-laget, se sikkerlogg for detaljer.")
            sikkerLogg.error("Feil ved transformering av json-body til UTF-8.", it)
            this.svar(Feilresponser.ugyldigBody)
        }.map { body ->
            Either.catch { deserialize<T>(body) }
                .onLeft {
                    log.error("Feil ved deserialisering av json-body inn mot web-laget, se sikkerlogg for detaljer.")
                    sikkerLogg.error("Feil ved deserialisering av json-body: $body", it)
                    this.svar(Feilresponser.ugyldigBody)
                }.onRight(ifRight)
        }
}
