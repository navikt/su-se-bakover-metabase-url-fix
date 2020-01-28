package no.nav.su.se.bakover.person

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.config.ApplicationConfig
import io.ktor.http.ContentType.Text
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.fromValue
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.Feil
import no.nav.su.se.bakover.Ok
import no.nav.su.se.bakover.azure.AzureClient
import no.nav.su.se.bakover.getProperty
import org.slf4j.LoggerFactory

const val personPath = "/person"
const val identLabel = "ident"

private val sikkerLogg = LoggerFactory.getLogger("sikkerLogg")

@KtorExperimentalAPI
fun Route.personRoutes(config: ApplicationConfig, azureClient: AzureClient, personClient: SuPersonClient) {
    get(personPath) {
        call.parameters[identLabel]?.let { personIdent ->
            val principal = (call.authentication.principal as JWTPrincipal).payload
            sikkerLogg.info("${principal.subject} gjør oppslag på person $personIdent")
            val suPersonToken = azureClient.onBehalfOFToken(call.request.header(Authorization)!!, config.getProperty("integrations.suPerson.clientId"))

            when (val response = personClient.person(personIdent, suPersonToken)) {
                is Ok -> call.respond(OK, response.json)
                is Feil -> call.respondText(response.message, Text.Plain, fromValue(response.httpCode))
            }
        } ?: call.respond(BadRequest, "query param '$identLabel' må oppgis")
    }
}
