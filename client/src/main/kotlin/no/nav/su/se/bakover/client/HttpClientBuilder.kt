package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.azure.AzureClient
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.DokArkivClient
import no.nav.su.se.bakover.client.inntekt.InntektOppslag
import no.nav.su.se.bakover.client.inntekt.SuInntektClient
import no.nav.su.se.bakover.client.kodeverk.Kodeverk
import no.nav.su.se.bakover.client.kodeverk.KodeverkHttpClient
import no.nav.su.se.bakover.client.oppgave.OppgaveHttpClient
import no.nav.su.se.bakover.client.pdf.PdfClient
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonClient
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.client.sts.StsClient
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.client.stubs.dokarkiv.DokArkivStub
import no.nav.su.se.bakover.client.stubs.inntekt.InntektOppslagStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveClientStub
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import org.slf4j.LoggerFactory

interface HttpClientsBuilder {
    fun build(
        azure: OAuth = HttpClientBuilder.azure(),
        tokenOppslag: TokenOppslag = HttpClientBuilder.token(),
        kodeverk: Kodeverk = HttpClientBuilder.kodeverk(consumerId = "srvsupstonad"),
        personOppslag: PersonOppslag = HttpClientBuilder.person(oAuth = azure, tokenOppslag = tokenOppslag, kodeverk = kodeverk),
        inntektOppslag: InntektOppslag = HttpClientBuilder.inntekt(oAuth = azure, personOppslag = personOppslag),
        pdfGenerator: PdfGenerator = HttpClientBuilder.pdf(),
        dokArkiv: DokArkiv = HttpClientBuilder.dokArkiv(tokenOppslag = tokenOppslag),
        oppgaveClient: OppgaveClient = HttpClientBuilder.oppgave(tokenOppslag = tokenOppslag)
    ): HttpClients
}

data class HttpClients(
    val oauth: OAuth,
    val personOppslag: PersonOppslag,
    val inntektOppslag: InntektOppslag,
    val tokenOppslag: TokenOppslag,
    val pdfGenerator: PdfGenerator,
    val dokArkiv: DokArkiv,
    val oppgaveClient: OppgaveClient,
    val kodeverk: Kodeverk
)

object HttpClientBuilder : HttpClientsBuilder {
    internal fun azure(
        clientId: String = getAzureClientId(),
        clientSecret: String = Config.azureClientSecret,
        wellknownUrl: String = Config.azureWellKnownUrl
    ): OAuth {
        return AzureClient(clientId, clientSecret, wellknownUrl)
    }

    private fun getAzureClientId() = Config.azureClientId

    internal fun person(
        kodeverk: Kodeverk,
        baseUrl: String = Config.pdlUrl,
        clientId: String = getAzureClientId(),
        oAuth: OAuth,
        tokenOppslag: TokenOppslag,
    ): PersonOppslag = when (Config.isLocalOrRunningTests) {
        true -> PersonOppslagStub.also { logger.warn("********** Using stub for ${PersonOppslag::class.java} **********") }
        else -> PersonClient(kodeverk, baseUrl, tokenOppslag, clientId, oAuth)
    }

    internal fun inntekt(
        baseUrl: String = Config.suInntektUrl,
        clientId: String = Config.suInntektAzureClientId,
        oAuth: OAuth,
        personOppslag: PersonOppslag
    ): InntektOppslag = when (Config.isLocalOrRunningTests) {
        true -> InntektOppslagStub.also { logger.warn("********** Using stub for ${InntektOppslag::class.java} **********") }
        else -> SuInntektClient(
            baseUrl,
            clientId,
            oAuth,
            personOppslag
        )
    }

    internal fun token(
        baseUrl: String = Config.stsUrl,
        username: String = Config.stsUsername,
        password: String = Config.stsPassword
    ): TokenOppslag = when (Config.isLocalOrRunningTests) {
        true -> TokenOppslagStub.also { logger.warn("********** Using stub for ${TokenOppslag::class.java} **********") }
        else -> StsClient(baseUrl, username, password)
    }

    internal fun pdf(
        baseUrl: String = Config.pdfgenUrl
    ): PdfGenerator = when (Config.isLocalOrRunningTests) {
        true -> {
            if (Config.pdfgenLocal) {
                PdfClient("http://localhost:8081")
            } else {
                PdfGeneratorStub.also { logger.warn("********** Using stub for ${PdfGenerator::class.java} **********") }
            }
        }
        else -> PdfClient(baseUrl)
    }

    internal fun dokArkiv(
        baseUrl: String = Config.dokarkivUrl,
        tokenOppslag: TokenOppslag
    ): DokArkiv = when (Config.isLocalOrRunningTests) {
        true -> DokArkivStub.also { logger.warn("********** Using stub for ${DokArkiv::class.java} **********") }
        else -> DokArkivClient(baseUrl, tokenOppslag)
    }

    internal fun oppgave(
        baseUrl: String = Config.oppgaveUrl,
        tokenOppslag: TokenOppslag
    ): OppgaveClient = when (Config.isLocalOrRunningTests) {
        true -> OppgaveClientStub.also { logger.warn("********** Using stub for ${OppgaveClient::class.java} **********") }
        else -> OppgaveHttpClient(baseUrl, tokenOppslag)
    }

    internal fun kodeverk(
        baseUrl: String = Config.kodeverkUrl,
        consumerId: String
    ): Kodeverk = KodeverkHttpClient(baseUrl, consumerId)

    override fun build(
        azure: OAuth,
        tokenOppslag: TokenOppslag,
        kodeverk: Kodeverk,
        personOppslag: PersonOppslag,
        inntektOppslag: InntektOppslag,
        pdfGenerator: PdfGenerator,
        dokArkiv: DokArkiv,
        oppgaveClient: OppgaveClient
    ): HttpClients {
        return HttpClients(azure, personOppslag, inntektOppslag, tokenOppslag, pdfGenerator, dokArkiv, oppgaveClient, kodeverk)
    }

    private val logger = LoggerFactory.getLogger(HttpClientBuilder::class.java)
}
