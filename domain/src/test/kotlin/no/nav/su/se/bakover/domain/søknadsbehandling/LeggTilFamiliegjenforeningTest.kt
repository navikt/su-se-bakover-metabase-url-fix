package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldBeRight
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.test.beregnetSøknadsbehandling
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nySakAlder
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertSøknadsbehandling
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknad.søknadsinnholdAlder
import no.nav.su.se.bakover.test.søknadsbehandlingBeregnetAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkårInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningAvslag
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningInnvilget

internal class LeggTilFamiliegjenforeningTest {

    @Test
    fun `kan legge til familiegjenforening ved uavklart`() {
        val fnr = Fnr.generer()
        val uavklart = nySøknadsbehandlingMedStønadsperiode(
            sakOgSøknad = nySakMedjournalførtSøknadOgOppgave(
                fnr = fnr,
                søknadInnhold = søknadsinnholdAlder(personopplysninger = Personopplysninger(fnr)),
            ),
        )

        uavklart.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til familiegjenforening ved vilkårsvurdert innvilget`() {
        val innvilget =
            søknadsbehandlingVilkårsvurdertInnvilget(
                sakOgSøknad = nySakAlder(),
            )

        innvilget.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til familiegjenforening ved vilkårsvurdert avslag`() {
        val avslag =
            søknadsbehandlingVilkårsvurdertAvslag(
                sakOgSøknad = nySakAlder(),
            )

        avslag.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved beregnet innvilget`() {
        val innvilget =
            beregnetSøknadsbehandling(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder().vilkår.toList())

        innvilget.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved beregnet avslag`() {
        val avslag =
            søknadsbehandlingBeregnetAvslag(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder().vilkår.toList())

        avslag.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved simulert`() {
        val simulert =
            simulertSøknadsbehandling(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder().vilkår.toList())

        simulert.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved underkjentInnvilget`() {
        val iverksattInnvilget =
            søknadsbehandlingUnderkjentInnvilget(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder().vilkår.toList())

        iverksattInnvilget.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved underkjentAvslagMedBeregning`() {
        val iverksattInnvilget =
            søknadsbehandlingUnderkjentAvslagMedBeregning(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder().vilkår.toList())

        iverksattInnvilget.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til familiegjenforening ved underkjentAvslagUtenBeregning`() {
        val iverksattInnvilget =
            søknadsbehandlingUnderkjentAvslagUtenBeregning(
                sakOgSøknad = nySakAlder(),
            )

        iverksattInnvilget.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }
}
