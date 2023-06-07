package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningVurderinger
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknad.søknadsinnholdAlder
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandling
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

internal class SøknadsbehandlingServiceLeggTilFamiliegjenforeningTest {

    @Test
    fun `fant ikke behandling`() {
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn null
            },
        ).let {
            it.søknadsbehandlingService.leggTilFamiliegjenforeningvilkår(
                request = LeggTilFamiliegjenforeningRequest(
                    behandlingId = UUID.randomUUID(),
                    vurderinger = listOf(
                        FamiliegjenforeningVurderinger(FamiliegjenforeningvilkårStatus.Uavklart),
                    ),
                ),
                saksbehandler = saksbehandler,
            ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService.FantIkkeBehandling.left()
        }
    }

    @Test
    fun `får lagt til familiegjenforening vilkår`() {
        val fnr = Fnr.generer()
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn vilkårsvurdertSøknadsbehandling(
                    sakOgSøknad = nySakMedjournalførtSøknadOgOppgave(
                        fnr = fnr,
                        søknadInnhold = søknadsinnholdAlder(personopplysninger = Personopplysninger(fnr)),
                    ),
                    customVilkår = listOf(
                        FormueVilkår.IkkeVurdert,
                        LovligOppholdVilkår.IkkeVurdert,
                        FastOppholdINorgeVilkår.IkkeVurdert,
                        InstitusjonsoppholdVilkår.IkkeVurdert,
                        UtenlandsoppholdVilkår.IkkeVurdert,
                        PersonligOppmøteVilkår.IkkeVurdert,
                        OpplysningspliktVilkår.IkkeVurdert,
                        PensjonsVilkår.IkkeVurdert,
                        FamiliegjenforeningVilkår.IkkeVurdert,
                    ),
                ).second
            },
        ).let { søknadsbehandlingServiceAndMocks ->
            val behandlingId = UUID.randomUUID()
            val actual = søknadsbehandlingServiceAndMocks.søknadsbehandlingService.leggTilFamiliegjenforeningvilkår(
                request = LeggTilFamiliegjenforeningRequest(
                    behandlingId = behandlingId,
                    vurderinger = listOf(
                        FamiliegjenforeningVurderinger(FamiliegjenforeningvilkårStatus.VilkårOppfylt),
                    ),
                ),
                saksbehandler = saksbehandler,
            ).getOrFail()

            actual.let {
                it.vilkårsvurderinger.familiegjenforening().shouldBeRight().let {
                    it.vurdering shouldBe Vurdering.Innvilget
                }
            }

            verify(søknadsbehandlingServiceAndMocks.søknadsbehandlingRepo).hent(behandlingId)
        }
    }

    @Test
    fun `kaster hvis vurderinger ikke har noen elementer`() {
        assertThrows<IllegalArgumentException> {
            SøknadsbehandlingServiceAndMocks(
                søknadsbehandlingRepo = mock {
                    on { hent(any()) } doReturn vilkårsvurdertSøknadsbehandling(
                        sakOgSøknad = nySakMedjournalførtSøknadOgOppgave(),
                        customVilkår = listOf(
                            FormueVilkår.IkkeVurdert,
                            LovligOppholdVilkår.IkkeVurdert,
                            FastOppholdINorgeVilkår.IkkeVurdert,
                            InstitusjonsoppholdVilkår.IkkeVurdert,
                            UtenlandsoppholdVilkår.IkkeVurdert,
                            PersonligOppmøteVilkår.IkkeVurdert,
                            OpplysningspliktVilkår.IkkeVurdert,
                            PensjonsVilkår.IkkeVurdert,
                            FamiliegjenforeningVilkår.IkkeVurdert,
                        ),
                    ).second
                },
            ).let { søknadsbehandlingServiceAndMocks ->
                val behandlingId = UUID.randomUUID()
                søknadsbehandlingServiceAndMocks.søknadsbehandlingService.leggTilFamiliegjenforeningvilkår(
                    request = LeggTilFamiliegjenforeningRequest(
                        behandlingId = behandlingId,
                        vurderinger = emptyList(),
                    ),
                    saksbehandler = saksbehandler,
                )
            }
        }
    }
}
