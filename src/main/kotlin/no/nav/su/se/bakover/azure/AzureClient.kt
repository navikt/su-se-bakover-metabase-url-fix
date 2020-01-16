package no.nav.su.se.bakover.azure

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.HttpHeaders.ContentType
import org.json.JSONObject
import org.slf4j.LoggerFactory

class AzureClient(
        private val clientId: String,
        private val clientSecret: String,
        private val tokenEndpoint: String
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(AzureClient::class.java)
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val REQUESTED_TOKEN_USE = "on_behalf_of"
    }

    fun onBehalfOFToken(originalToken: String, otherAppId: String): String {
        val (_, _, result) = tokenEndpoint.httpPost()
                .header(ContentType, FormUrlEncoded)
                .body(JSONObject(mapOf(
                        "grant_type" to GRANT_TYPE,
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "assertion" to originalToken,
                        "scope" to "api://$otherAppId",
                        "requested_token_use" to REQUESTED_TOKEN_USE
                )).toString())
                .responseString()

        result.fold(
                { JSONObject(it) },
                { throw RuntimeException("Error while exchanging token in Azure, error:$it}") }
        ).also {
            if (it.has("error")) {
                throw RuntimeException("Error while exchanging token in Azure, error:$it}")
            }
            return it.getString("access_token")
        }
    }
}


fun getJWKConfig(wellKnownUrl: String): JSONObject {
    val (_, _, result) = wellKnownUrl.httpGet().responseString()
    return result.fold(
            { JSONObject(it) },
            { throw RuntimeException("Could not get JWK config from url ${wellKnownUrl}, error:${it}") }
    )
}