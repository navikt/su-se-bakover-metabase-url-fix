package no.nav.su.se.bakover.client.dokdistfordeling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.sts.TokenOppslag
import org.json.JSONObject
import org.slf4j.MDC
import org.slf4j.LoggerFactory

internal const val dokDistFordelingPath = "/rest/v1/distribuerjournalpost"
class DokDistFordelingClient(val baseUrl: String, val tokenOppslag: TokenOppslag) : DokDistFordeling {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun bestillDistribusjon(
        journalPostId: String
    ): Either<ClientError, String> {
        val body = byggDistribusjonPostJson(journalPostId)
        val (_, response, result) = "$baseUrl$dokDistFordelingPath".httpPost()
            .authentication().bearer(tokenOppslag.token())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .body(
                body
            ).responseString()

        return result.fold(
            {
                json ->
                JSONObject(json).let {
                    it.optString("bestillingsId").right()
                }
            },
            {
                log.warn("Feil ved bestilling av distribusjon.", it)
                ClientError(response.statusCode, "Feil ved bestilling av distribusjon.").left()
            }
        )
    }

    fun byggDistribusjonPostJson(journalPostId: String): String {
        return """
                    {
                        "journalPostId": "$journalPostId",
                        "bestillendeFagsystem": "SUPSTONAD",
                        "dokumentProdApp": "su-se-bakover"
                    }
        """.trimIndent()
    }
}
