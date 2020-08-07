package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.azure.AzureClient
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.DokArkivClient
import no.nav.su.se.bakover.client.inntekt.InntektOppslag
import no.nav.su.se.bakover.client.inntekt.SuInntektClient
import no.nav.su.se.bakover.client.oppgave.Oppgave
import no.nav.su.se.bakover.client.oppgave.OppgaveClient
import no.nav.su.se.bakover.client.pdf.PdfClient
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PdlClient
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.client.sts.StsClient
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.client.stubs.dokarkiv.DokArkivStub
import no.nav.su.se.bakover.client.stubs.inntekt.InntektOppslagStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveStub
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.isLocalOrRunningTests
import org.slf4j.LoggerFactory

interface HttpClientsBuilder {
    fun build(
        azure: OAuth = HttpClientBuilder.azure(),
        tokenOppslag: TokenOppslag = HttpClientBuilder.token(),
        personOppslag: PersonOppslag = HttpClientBuilder.person(oAuth = azure, tokenOppslag = tokenOppslag),
        inntektOppslag: InntektOppslag = HttpClientBuilder.inntekt(oAuth = azure, personOppslag = personOppslag),
        pdfGenerator: PdfGenerator = HttpClientBuilder.pdf(),
        dokArkiv: DokArkiv = HttpClientBuilder.dokArkiv(tokenOppslag = tokenOppslag),
        oppgave: Oppgave = HttpClientBuilder.oppgave(tokenOppslag = tokenOppslag)
    ): HttpClients
}

data class HttpClients(
    val oauth: OAuth,
    val personOppslag: PersonOppslag,
    val inntektOppslag: InntektOppslag,
    val tokenOppslag: TokenOppslag,
    val pdfGenerator: PdfGenerator,
    val dokArkiv: DokArkiv,
    val oppgave: Oppgave
)

object HttpClientBuilder : HttpClientsBuilder {
    private val env = System.getenv()
    internal fun azure(
        clientId: String = env.getOrDefault("AZURE_CLIENT_ID", "24ea4acb-547e-45de-a6d3-474bd8bed46e"),
        clientSecret: String = env.getOrDefault("AZURE_CLIENT_SECRET", "secret"),
        wellknownUrl: String = env.getOrDefault(
            "AZURE_WELLKNOWN_URL",
            "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0/.well-known/openid-configuration"
        )
    ): OAuth {
        return AzureClient(clientId, clientSecret, wellknownUrl)
    }

    internal fun person(
        baseUrl: String = env.getOrDefault("SU_PERSON_URL", "http://su-person.default.svc.nais.local"),
        clientId: String = env.getOrDefault("SU_PERSON_AZURE_CLIENT_ID", "76de0063-2696-423b-84a4-19d886c116ca"),
        oAuth: OAuth,
        tokenOppslag: TokenOppslag
    ): PersonOppslag = when (env.isLocalOrRunningTests()) {
        true -> PersonOppslagStub.also { logger.warn("********** Using stub for ${PersonOppslag::class.java} **********") }
        else -> PdlClient(baseUrl, tokenOppslag, clientId, oAuth)
    }

    internal fun inntekt(
        baseUrl: String = env.getOrDefault("SU_INNTEKT_URL", "http://su-inntekt.default.svc.nais.local"),
        clientId: String = env.getOrDefault("SU_INNTEKT_AZURE_CLIENT_ID", "9cd61904-33ad-40e8-9cc8-19e4dab588c5"),
        oAuth: OAuth,
        personOppslag: PersonOppslag
    ): InntektOppslag = when (env.isLocalOrRunningTests()) {
        true -> InntektOppslagStub.also { logger.warn("********** Using stub for ${InntektOppslag::class.java} **********") }
        else -> SuInntektClient(
            baseUrl,
            clientId,
            oAuth,
            personOppslag
        )
    }

    internal fun token(
        baseUrl: String = env.getOrDefault("STS_URL", "http://security-token-service.default.svc.nais.local"),
        username: String = env.getOrDefault("username", "username"),
        password: String = env.getOrDefault("password", "password")
    ): TokenOppslag = when (env.isLocalOrRunningTests()) {
        true -> TokenOppslagStub.also { logger.warn("********** Using stub for ${TokenOppslag::class.java} **********") }
        else -> StsClient(baseUrl, username, password)
    }

    internal fun pdf(
        baseUrl: String = env.getOrDefault("PDFGEN_URL", "http://su-pdfgen.default.svc.nais.local")
    ): PdfGenerator = when (env.isLocalOrRunningTests()) {
        true -> PdfGeneratorStub.also { logger.warn("********** Using stub for ${PdfGenerator::class.java} **********") }
        else -> PdfClient(baseUrl)
    }

    internal fun dokArkiv(
        baseUrl: String = env.getOrDefault("DOKARKIV_URL", "http://dokarkiv.default.svc.nais.local"),
        tokenOppslag: TokenOppslag
    ): DokArkiv = when (env.isLocalOrRunningTests()) {
        true -> DokArkivStub.also { logger.warn("********** Using stub for ${DokArkiv::class.java} **********") }
        else -> DokArkivClient(baseUrl, tokenOppslag)
    }

    internal fun oppgave(
        baseUrl: String = env.getOrDefault("OPPGAVE_URL", "http://oppgave.default.svc.nais.local"),
        tokenOppslag: TokenOppslag
    ): Oppgave = when (env.isLocalOrRunningTests()) {
        true -> OppgaveStub.also { logger.warn("********** Using stub for ${Oppgave::class.java} **********") }
        else -> OppgaveClient(baseUrl, tokenOppslag)
    }

    override fun build(
        azure: OAuth,
        tokenOppslag: TokenOppslag,
        personOppslag: PersonOppslag,
        inntektOppslag: InntektOppslag,
        pdfGenerator: PdfGenerator,
        dokArkiv: DokArkiv,
        oppgave: Oppgave
    ): HttpClients {
        return HttpClients(azure, personOppslag, inntektOppslag, tokenOppslag, pdfGenerator, dokArkiv, oppgave)
    }

    private val logger = LoggerFactory.getLogger(HttpClientBuilder::class.java)
}
