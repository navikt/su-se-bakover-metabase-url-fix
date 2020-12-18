package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.azure.AzureClient
import no.nav.su.se.bakover.client.dkif.DkifClient
import no.nav.su.se.bakover.client.dokarkiv.DokArkivClient
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordelingClient
import no.nav.su.se.bakover.client.kafka.KafkaPublisherClient
import no.nav.su.se.bakover.client.kodeverk.KodeverkHttpClient
import no.nav.su.se.bakover.client.nais.LeaderPodLookupClient
import no.nav.su.se.bakover.client.oppdrag.IbmMqPublisher
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.MqPublisherConfig
import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingMqPublisher
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringConfig
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringSoapClient
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingMqPublisher
import no.nav.su.se.bakover.client.oppgave.OppgaveHttpClient
import no.nav.su.se.bakover.client.pdf.PdfClient
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.client.person.PersonClient
import no.nav.su.se.bakover.client.skjerming.SkjermingClient
import no.nav.su.se.bakover.client.sts.StsClient
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.common.JmsConfig
import java.time.Clock

data class ProdClientsBuilder(
    private val jmsConfig: JmsConfig,
) : ClientsBuilder {

    override fun build(applicationConfig: ApplicationConfig): Clients {
        val consumerId = "srvsupstonad"

        val azureConfig = applicationConfig.azure
        val oAuth = AzureClient(azureConfig.clientId, azureConfig.clientSecret, azureConfig.wellKnownUrl)
        val kodeverk = KodeverkHttpClient(Config.kodeverkUrl, consumerId)
        val tokenOppslag = StsClient(Config.stsUrl, applicationConfig.serviceUser.username, applicationConfig.serviceUser.password)
        val dkif = DkifClient(Config.dkifUrl, tokenOppslag, consumerId)
        val personOppslag =
            PersonClient(Config.pdlUrl, kodeverk, SkjermingClient(Config.skjermingUrl), dkif, tokenOppslag)

        return Clients(
            oauth = oAuth,
            personOppslag = personOppslag,
            tokenOppslag = tokenOppslag,
            pdfGenerator = PdfClient(Config.pdfgenUrl),
            dokArkiv = DokArkivClient(Config.dokarkivUrl, tokenOppslag),
            oppgaveClient = OppgaveHttpClient(
                baseUrl = Config.oppgaveUrl,
                exchange = oAuth,
                oppgaveClientId = Config.oppgaveClientId,
                clock = Clock.systemUTC(),
            ),
            kodeverk = kodeverk,
            simuleringClient = SimuleringSoapClient(
                SimuleringConfig(
                    simuleringServiceUrl = applicationConfig.oppdrag.simulering.url,
                    stsSoapUrl = applicationConfig.oppdrag.simulering.stsSoapUrl,
                    disableCNCheck = true,
                    serviceUser = applicationConfig.serviceUser
                ).wrapWithSTSSimulerFpService()
            ),
            utbetalingPublisher = UtbetalingMqPublisher(
                mqPublisher = applicationConfig.oppdrag.let {
                    IbmMqPublisher(
                        MqPublisherConfig(
                            sendQueue = it.utbetaling.mqSendQueue,
                            replyTo = it.utbetaling.mqReplyTo
                        ),
                        jmsContext = jmsConfig.jmsContext
                    )
                }
            ),
            dokDistFordeling = DokDistFordelingClient(Config.dokDistUrl, tokenOppslag),
            avstemmingPublisher = AvstemmingMqPublisher(
                mqPublisher = IbmMqPublisher(
                    MqPublisherConfig(
                        sendQueue = applicationConfig.oppdrag.avstemming.mqSendQueue
                    ),
                    jmsContext = jmsConfig.jmsContext
                )
            ),
            microsoftGraphApiClient = MicrosoftGraphApiClient(oAuth),
            digitalKontaktinformasjon = dkif,
            leaderPodLookup = LeaderPodLookupClient(Config.leaderPodLookupPath),
            kafkaPublisher = KafkaPublisherClient()
        )
    }
}
