package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.vilkår.LeggTilOppholdIUtlandetRequest
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class SøknadsbehandlingLeggTilOppholdIUtlandetTest {
    @Test
    fun `svarer med feil hvis ikke behandling fins`() {
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock { on { hent(any()) } doReturn null },
        ).let {
            it.søknadsbehandlingService.leggTilOppholdIUtlandet(
                LeggTilOppholdIUtlandetRequest(
                    behandlingId = UUID.randomUUID(),
                    status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
                    begrunnelse = "",
                ),
            ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilOppholdIUtlandet.FantIkkeBehandling.left()

            verify(it.søknadsbehandlingRepo).hent(any())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil hvis ikke vilkår er ugyldige`() {
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock { on { hent(any()) } doReturn søknadsbehandlingVilkårsvurdertUavklart().second },
        ).let {
            it.søknadsbehandlingService.leggTilOppholdIUtlandet(
                // I praksis ikke mulig at dette tryner per nå
                mock {
                    on { behandlingId } doReturn UUID.randomUUID()
                    on {
                        toVilkår(
                            any(),
                            any(),
                        )
                    } doReturn LeggTilOppholdIUtlandetRequest.UgyldigOppholdIUtlandet.OverlappendeVurderingsperioder.left()
                },
            ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilOppholdIUtlandet.OverlappendeVurderingsperioder.left()

            verify(it.søknadsbehandlingRepo).hent(any())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil hvis behandling er i ugyldig tilstand for å legge til opphold i utlandet`() {
        val iverksatt = søknadsbehandlingIverksattInnvilget().second
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock { on { hent(any()) } doReturn iverksatt },
        ).let {
            it.søknadsbehandlingService.leggTilOppholdIUtlandet(
                LeggTilOppholdIUtlandetRequest(
                    behandlingId = iverksatt.id,
                    status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
                    begrunnelse = "jahoo",
                ),
            ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilOppholdIUtlandet.UgyldigTilstand(
                fra = iverksatt::class, til = Søknadsbehandling.Vilkårsvurdert::class,
            ).left()

            verify(it.søknadsbehandlingRepo).hent(any())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path`() {
        val innvilget = søknadsbehandlingVilkårsvurdertInnvilget().second
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn innvilget
                on { defaultSessionContext() } doReturn TestSessionFactory.transactionContext
            },
        ).let { serviceAndMocks ->
            serviceAndMocks.søknadsbehandlingService.leggTilOppholdIUtlandet(
                LeggTilOppholdIUtlandetRequest(
                    behandlingId = innvilget.id,
                    status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
                    begrunnelse = "jahoo",
                ),
            ) shouldBe innvilget.right()

            verify(serviceAndMocks.søknadsbehandlingRepo).hent(any())
            verify(serviceAndMocks.søknadsbehandlingRepo).lagre(
                argThat { it shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>() },
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.søknadsbehandlingRepo).defaultSessionContext()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `vilkårene vurderes på nytt når nye utlandsopphold legges til`() {
        val innvilget = søknadsbehandlingVilkårsvurdertInnvilget().second
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn innvilget
                on { defaultSessionContext() } doReturn TestSessionFactory.transactionContext
            },
        ).let { serviceAndMocks ->
            serviceAndMocks.søknadsbehandlingService.leggTilOppholdIUtlandet(
                LeggTilOppholdIUtlandetRequest(
                    behandlingId = innvilget.id,
                    status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet,
                    begrunnelse = "jahoo",
                ),
            )

            verify(serviceAndMocks.søknadsbehandlingRepo).hent(any())
            verify(serviceAndMocks.søknadsbehandlingRepo).lagre(
                argThat { it shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>() },
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.søknadsbehandlingRepo).defaultSessionContext()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }
}
