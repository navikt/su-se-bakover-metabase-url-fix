package no.nav.su.se.bakover.inntekt

import com.github.kittinunf.fuel.core.*
import com.github.kittinunf.fuel.toolbox.HttpClient
import io.ktor.http.HttpHeaders.XRequestId
import no.nav.su.se.bakover.Feil
import no.nav.su.se.bakover.Ok
import no.nav.su.se.bakover.Resultat
import no.nav.su.se.bakover.azure.TokenExchange
import no.nav.su.se.bakover.person.PersonOppslag
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.net.URL
import kotlin.test.assertEquals

internal class InntektClientTest {

    @Test
    fun `skal ikke kalle inntekt om person gir feil`() {
        val inntektClient = SuInntektClient(url, clientId, tokenExchange, persontilgang403)
        val result = inntektClient.inntekt("noen", "innlogget bruker", "2000-01", "2000-12")
        assertEquals(Feil(403, "Du hakke lov"), result)
    }

    @Test
    fun `skal kalle inntekt om person gir OK`() {
        val inntektClient = SuInntektClient(url, clientId, tokenExchange, persontilgang200)
        val result = inntektClient.inntekt("noen", "innlogget bruker", "2000-01", "2000-12")
        assertEquals(Ok(""), result)
    }

    private val url = "http://some.place"
    private val clientId = "inntektclientid"
    private val persontilgang200 = object : PersonOppslag {
        override fun person(ident: String, innloggetSaksbehandlerToken: String): Resultat =
            Ok("""{"ting": "OK"}""")
    }
    private val persontilgang403 = object : PersonOppslag {
        override fun person(ident: String, innloggetSaksbehandlerToken: String): Resultat =
            Feil(403, "Du hakke lov")
    }
    private val tokenExchange = object : TokenExchange {
        override fun onBehalfOFToken(originalToken: String, otherAppId: String): String = "ON BEHALF OF!"
    }

    @BeforeEach
    fun setup() {
        MDC.put(XRequestId, "a request id")
        FuelManager.instance.client = object : Client {
            override fun executeRequest(request: Request): Response = okResponseFromInntekt()
        }
    }

    @AfterEach
    fun tearDown() {
        MDC.clear()
        FuelManager.instance.client = HttpClient(FuelManager.instance.proxy, hook = FuelManager.instance.hook)
    }

    private fun okResponseFromInntekt() = Response(
        url = URL("http://some.place"),
        contentLength = 0,
        headers = Headers(),
        responseMessage = "Thumbs up",
        statusCode = 200
    )
}