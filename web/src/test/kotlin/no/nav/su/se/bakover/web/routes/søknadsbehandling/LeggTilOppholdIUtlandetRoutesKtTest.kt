package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class LeggTilOppholdIUtlandetRoutesKtTest {
    @Test
    fun `svarer med feilmelding ved ugyldig body`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/vilkår/oppholdIUtlandet",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "bogus": "body"
                    }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Ugyldig body"
            }
        }
    }

    @Test
    fun `svarer med feilmelding fra service`() {
        withTestApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services().copy(
                        søknadsbehandling = mock {
                            on { leggTilOppholdIUtlandet(any()) } doReturn SøknadsbehandlingService.KunneIkkeLeggeTilOppholdIUtlandet.FantIkkeBehandling.left()
                        },
                    ),
                )
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/vilkår/oppholdIUtlandet",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "status": "SkalHoldeSegINorge",
                        "begrunnelse": "jawol"
                    }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Fant ikke behandling"
            }
        }
    }

    @Test
    fun `svarer med søknadsbehandling ved suksess`() {
        val vilkårsvurdert = søknadsbehandlingVilkårsvurdertInnvilget().second
        withTestApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services().copy(
                        søknadsbehandling = mock {
                            on { leggTilOppholdIUtlandet(any()) } doReturn vilkårsvurdert.right()
                        },
                    ),
                )
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/vilkår/oppholdIUtlandet",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "status": "SkalHoldeSegINorge",
                        "begrunnelse": "jawol"
                    }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                JSONAssert.assertEquals(response.content, serialize(vilkårsvurdert.toJson()), true)
            }
        }
    }
}
