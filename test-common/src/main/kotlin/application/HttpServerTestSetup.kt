package no.nav.su.se.bakover.test.application

import dokument.domain.brev.BrevService
import dokument.domain.hendelser.DokumentHendelseRepo
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.routing.Route
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.config.TilbakekrevingConfig
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.jms.JmsConfig
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.dokument.infrastructure.database.Dokumentkomponenter
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.metrics.ClientMetrics
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingUnderRevurderingService
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.jwt.asBearerToken
import no.nav.su.se.bakover.test.jwt.jwtStub
import no.nav.su.se.bakover.web.Consumers
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.Services
import no.nav.su.se.bakover.web.susebakover
import org.mockito.kotlin.mock
import org.slf4j.MDC
import person.domain.PersonService
import satser.domain.SatsFactory
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlagTilHendelse
import tilbakekreving.presentation.Tilbakekrevingskomponenter
import vilkår.formue.domain.FormuegrenserFactory
import økonomi.application.utbetaling.ResendUtbetalingService
import java.time.Clock

/**
 * Contains setup-code for web-server-tests (routes).
 * Defaultly mocks everything except what is required to run the routes.
 */
fun Application.runApplicationWithMocks(
    clock: Clock = fixedClock,
    behandlingMetrics: BehandlingMetrics = mock(),
    søknadMetrics: SøknadMetrics = mock(),
    clientMetrics: ClientMetrics = mock(),
    dbMetrics: DbMetrics = mock(),
    applicationConfig: ApplicationConfig = applicationConfig(),
    satsFactory: SatsFactoryForSupplerendeStønad = mock(),
    satsFactoryIDag: SatsFactory = mock(),
    formuegrenserFactoryIDag: FormuegrenserFactory = mock(),
    databaseRepos: DatabaseRepos = mockedDatabaseRepos(),
    jmsConfig: JmsConfig = mock(),
    clients: Clients = mockedClients(),
    services: Services = mockedServices(),
    tilbakekrevingskomponenter: (
        clock: Clock,
        sessionFactory: SessionFactory,
        personService: PersonService,
        hendelsekonsumenterRepo: HendelsekonsumenterRepo,
        tilbakekrevingUnderRevurderingService: TilbakekrevingUnderRevurderingService,
        sakService: SakService,
        oppgaveService: OppgaveService,
        oppgaveHendelseRepo: OppgaveHendelseRepo,
        mapRåttKravgrunnlag: MapRåttKravgrunnlagTilHendelse,
        hendelseRepo: HendelseRepo,
        dokumentHendelseRepo: DokumentHendelseRepo,
        brevService: BrevService,
        tilbakekrevingConfig: TilbakekrevingConfig,
    ) -> Tilbakekrevingskomponenter = { clockFunParam, sessionFactory, personService, hendelsekonsumenterRepo, tilbakekrevingUnderRevurderingService, sak, oppgave, oppgaveHendelseRepo, mapRåttKravgrunnlagPåSakHendelse, hendelseRepo, dokumentHendelseRepo, brevService, tilbakekrevingConfig ->
        Tilbakekrevingskomponenter.create(
            clock = clockFunParam,
            sessionFactory = sessionFactory,
            personService = personService,
            hendelsekonsumenterRepo = hendelsekonsumenterRepo,
            tilbakekrevingUnderRevurderingService = tilbakekrevingUnderRevurderingService,
            sakService = sak,
            oppgaveService = oppgave,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
            mapRåttKravgrunnlagPåSakHendelse = mapRåttKravgrunnlagPåSakHendelse,
            hendelseRepo = hendelseRepo,
            dokumentHendelseRepo = dokumentHendelseRepo,
            brevService = brevService,
            tilbakekrevingConfig = tilbakekrevingConfig,
            dbMetrics = dbMetrics,
        )
    },
    dokumentkomponenter: Dokumentkomponenter = mock(),
    accessCheckProxy: AccessCheckProxy = AccessCheckProxy(databaseRepos.person, services),
    consumers: Consumers = mock(),
    beregningStrategyFactory: BeregningStrategyFactory = mock(),
    resendUtbetalingService: ResendUtbetalingService = mock(),
    extraRoutes: Route.(services: Services) -> Unit = {},
) {
    return susebakover(
        clock = clock,
        behandlingMetrics = behandlingMetrics,
        søknadMetrics = søknadMetrics,
        clientMetrics = clientMetrics,
        dbMetrics = dbMetrics,
        applicationConfig = applicationConfig,
        satsFactory = satsFactory,
        satsFactoryIDag = satsFactoryIDag,
        formuegrenserFactoryIDag = formuegrenserFactoryIDag,
        databaseRepos = databaseRepos,
        jmsConfig = jmsConfig,
        clients = clients,
        services = services,
        tilbakekrevingskomponenter = tilbakekrevingskomponenter,
        dokumentkomponenter = dokumentkomponenter,
        accessCheckProxy = accessCheckProxy,
        consumers = consumers,
        beregningStrategyFactory = beregningStrategyFactory,
        resendUtbetalingService = resendUtbetalingService,
        extraRoutes = extraRoutes,
        disableConsumersAndJobs = true,
    )
}

private const val DEFAULT_CALL_ID = "her skulle vi sikkert hatt en korrelasjonsid"

fun defaultRequest(
    method: HttpMethod,
    uri: String,
    roller: List<Brukerrolle> = emptyList(),
    navIdent: String = "Z990Lokal",
    correlationId: String = DEFAULT_CALL_ID,
    client: HttpClient,
    setup: HttpRequestBuilder.() -> Unit = {},
): HttpResponse {
    return runBlocking {
        client.request(uri) {
            val auth: String? = MDC.get("Authorization")
            val bearerToken = auth ?: jwtStub.createJwtToken(roller = roller, navIdent = navIdent).asBearerToken()
            this.method = method
            this.headers {
                append(HttpHeaders.XCorrelationId, correlationId)
                append(HttpHeaders.Authorization, bearerToken)
            }
            setup()
        }
    }
}
