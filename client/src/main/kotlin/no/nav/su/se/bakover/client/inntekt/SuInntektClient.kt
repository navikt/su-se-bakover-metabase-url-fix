package no.nav.su.se.bakover.client.inntekt

import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.person.PdlFeil
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.Fnr
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal class SuInntektClient(
    suInntektBaseUrl: String,
    private val suInntektClientId: String,
    private val exchange: OAuth,
    private val personOppslag: PersonOppslag
) : InntektOppslag {
    private val inntektResource = "$suInntektBaseUrl/inntekt"
    private val suInntektIdentLabel = "fnr"

    // TODO bedre håndtering av kode 6/7?
    override fun inntekt(
        ident: Fnr,
        innloggetSaksbehandlerToken: String,
        fomDato: String,
        tomDato: String
    ): ClientResponse {
        val oppslag = personOppslag.person(ident)
        return oppslag.fold(
            // TODO Hvorfor kan vi ikke returnere either med clientError
            { ClientResponse(httpCodeFor(it), it.message) },
            { finnInntekt(ident, innloggetSaksbehandlerToken, fomDato, tomDato) }
        )
    }

    private fun finnInntekt(
        ident: Fnr,
        innloggetSaksbehandlerToken: String,
        fomDato: String,
        tomDato: String
    ): ClientResponse {
        val onBehalfOfToken = exchange.onBehalfOFToken(innloggetSaksbehandlerToken, suInntektClientId)
        val (_, response, result) = inntektResource.httpPost(
            listOf(
                suInntektIdentLabel to ident.toString(),
                "fom" to fomDato.yearMonthSubstring(),
                "tom" to tomDato.yearMonthSubstring()
            )
        )
            .header("Authorization", "Bearer $onBehalfOfToken")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .responseString()

        return result.fold(
            { ClientResponse(response.statusCode, it) },
            { error ->
                val errorMessage = error.response.body().asString("application/json")
                val statusCode = error.response.statusCode
                logger.debug("Kall mot Inntektskomponenten feilet, statuskode: $statusCode, feilmelding: $errorMessage")
                ClientResponse(response.statusCode, errorMessage)
            }
        )
    }

    private fun httpCodeFor(pdlFeil: PdlFeil) = when (pdlFeil) {
        is PdlFeil.FantIkkePerson -> 404
        else -> 500
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SuInntektClient::class.java)
    }
}

private fun String.yearMonthSubstring() = substring(0, 7)
