package no.nav.su.se.bakover.client.stubs.azure

import no.nav.su.se.bakover.client.azure.OAuth
import org.json.JSONObject

object AzureClientStub : OAuth {
    override fun onBehalfOFToken(originalToken: String, otherAppId: String): String = originalToken

    override fun refreshTokens(refreshToken: String): JSONObject = throw NotImplementedError()

    override fun jwkConfig(): JSONObject {
        return JSONObject(
            mapOf(
                "authorization_endpoint" to "http://localhost:8080/login",
                "token_endpoint" to "http://localhost:8080/login",
                "issuer" to "localhost", // TODO connection to JWT token values
                "end_session_endpoint" to "http://localhost:8080/logout",
                "jwks_uri" to "http://localhost:8080/jwks"
            )
        )
    }

    override fun getSystemToken(otherAppId: String): String = throw NotImplementedError()
}
