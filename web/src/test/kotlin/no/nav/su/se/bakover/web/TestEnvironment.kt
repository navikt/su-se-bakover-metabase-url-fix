package no.nav.su.se.bakover.web

import com.nhaarman.mockitokotlin2.mock
import io.ktor.application.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.web.stubs.JwtStub

const val DEFAULT_CALL_ID = "her skulle vi sikkert hatt en korrelasjonsid"

val applicationConfig = ApplicationConfig(
    serviceUser = ApplicationConfig.ServiceUserConfig(
        username = "serviceUserTestUsername",
        password = "serviceUserTestPassword",
    ),
    azure = ApplicationConfig.AzureConfig(
        clientSecret = "testClientSecret",
        wellKnownUrl = "http://localhost/test/wellKnownUrl",
        clientId = "testClientId",
        backendCallbackUrl = "http://localhost/test/backendCallbackUrl",
        groups = ApplicationConfig.AzureConfig.AzureGroups(
            attestant = "testAzureGroupAttestant",
            saksbehandler = "testAzureGroupSaksbehandler",
            veileder = "testAzureGroupVeileder"
        )
    ),
    oppdrag = ApplicationConfig.OppdragConfig(
        mqQueueManager = "testMqQueueManager",
        mqPort = -22,
        mqHostname = "testMqHostname",
        mqChannel = "testMqChannel",
        utbetaling = ApplicationConfig.OppdragConfig.UtbetalingConfig(
            mqSendQueue = "testMqSendQueue",
            mqReplyTo = "testMqReplyTo"
        ),
        avstemming = ApplicationConfig.OppdragConfig.AvstemmingConfig(mqSendQueue = "avstemmingMqTestSendQueue"),
        simulering = ApplicationConfig.OppdragConfig.SimuleringConfig(
            url = "simuleringTestUrl",
            stsSoapUrl = "simuleringStsTestSoapUrl"
        )
    ),
    database = ApplicationConfig.DatabaseConfig(
        databaseName = "databaseTestname",
        jdbcUrl = "jdbcTestUrl",
        vaultMountPath = "",
    )
)

val jwtStub = JwtStub(applicationConfig)

internal fun Application.testSusebakover(
    clients: Clients = TestClientsBuilder.build(applicationConfig),
    behandlingFactory: BehandlingFactory = BehandlingFactory(mock()),
    databaseRepos: DatabaseRepos = DatabaseBuilder.build(EmbeddedDatabase.instance(), behandlingFactory),
    services: Services = ServiceBuilder( // build actual clients
        databaseRepos = databaseRepos,
        clients = clients,
        behandlingMetrics = mock(),
        søknadMetrics = mock()
    ).build()
) {
    return susebakover(
        behandlingFactory = BehandlingFactory(mock()),
        databaseRepos = databaseRepos,
        clients = clients,
        services = services,
        applicationConfig = applicationConfig,
    )
}

fun TestApplicationEngine.defaultRequest(
    method: HttpMethod,
    uri: String,
    roller: List<Brukerrolle>,
    setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall {
    return handleRequest(method, uri) {
        addHeader(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
        addHeader(HttpHeaders.Authorization, jwtStub.create(roller = roller))
        setup()
    }
}

fun TestApplicationEngine.requestSomAttestant(
    method: HttpMethod,
    uri: String,
    setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall {
    return handleRequest(method, uri) {
        addHeader(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
        addHeader(
            HttpHeaders.Authorization,
            jwtStub.create(roller = listOf(Brukerrolle.Attestant))
        )
        setup()
    }
}
