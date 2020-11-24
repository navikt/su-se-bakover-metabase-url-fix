package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import org.junit.jupiter.api.Test

internal class UførhetTest {

    @Test
    fun `status oppfylt men uføregrad er ikke spesifisert er ugyldig`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            uføregrad = null,
            forventetInntekt = 150
        ).erGyldig() shouldBe false
    }

    @Test
    fun `status oppfylt men forventet inntekt er ikke spesifisert er ugyldig`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            uføregrad = 10,
            forventetInntekt = null
        ).erGyldig() shouldBe false
    }

    @Test
    fun `status oppfylt er gyldig`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            uføregrad = 10,
            forventetInntekt = 150
        ).erGyldig() shouldBe true
    }

    @Test
    fun `status ikke oppfylt men uføregrad er spesifisert er ugyldig`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
            uføregrad = 10,
            forventetInntekt = null
        ).erGyldig() shouldBe false
    }

    @Test
    fun `status ikke oppfylt men forventet inntekt er spesifisert er ugyldig`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
            uføregrad = null,
            forventetInntekt = 1500
        ).erGyldig() shouldBe false
    }

    @Test
    fun `status ikke oppfylt er gyldig`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
            uføregrad = null,
            forventetInntekt = null
        ).erGyldig() shouldBe true
    }

    @Test
    fun `status har uføresak til behandling men uføregrad er ikke spesifsiert er ugyldig `() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling,
            uføregrad = null,
            forventetInntekt = 206
        ).erGyldig() shouldBe false
    }

    @Test
    fun `status har uføresak til behandling men forventet er ikke spesifsiert er ugyldig `() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling,
            uføregrad = 150,
            forventetInntekt = null
        ).erGyldig() shouldBe false
    }

    @Test
    fun `status har uføresak til behandling er gyldig`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling,
            uføregrad = 150,
            forventetInntekt = 512
        ).erGyldig() shouldBe true
    }

    @Test
    fun `er ikke ferdigbehandlet dersom status er har uføresak til behandlig`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling,
            uføregrad = 100,
            forventetInntekt = 100
        ).let {
            it.erVilkårOppfylt() shouldBe false
            it.erVilkårIkkeOppfylt() shouldBe false
        }
    }

    @Test
    fun `vilkår er oppfylt dersom status er oppfylt`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            uføregrad = 100,
            forventetInntekt = 100
        ).erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `vilkår er ikke oppfylt dersom status er oppfylt`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
            uføregrad = 100,
            forventetInntekt = 100
        ).erVilkårOppfylt() shouldBe false
    }

    @Test
    fun `vilkår er oppfylt dersom status er uføresak til behandling`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling,
            uføregrad = 100,
            forventetInntekt = 100
        ).erVilkårOppfylt() shouldBe false
    }

    @Test
    fun `avslagsgrunn er uførhet dersom status er ikke oppfylt`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
            uføregrad = 100,
            forventetInntekt = 100
        ).avslagsgrunn() shouldBe Avslagsgrunn.UFØRHET
    }

    @Test
    fun `avslagsgrunn er null dersom status er oppfylt`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            uføregrad = 100,
            forventetInntekt = 100
        ).avslagsgrunn() shouldBe null
    }
}
