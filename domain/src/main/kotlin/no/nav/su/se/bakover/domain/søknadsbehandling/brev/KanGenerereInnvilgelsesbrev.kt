@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Søknadsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package no.nav.su.se.bakover.domain.søknadsbehandling

import beregning.domain.Beregning
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.grunnlag.harForventetInntektStørreEnn0
import satser.domain.SatsFactory
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.harEPS

sealed interface KanGenerereInnvilgelsesbrev : KanGenerereBrev {
    override val beregning: Beregning

    override fun lagBrevutkastCommandForSaksbehandler(
        satsFactory: SatsFactory,
        utførtAv: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ) = lagDokumentCommand(
        satsFactory = satsFactory,
        fritekst = fritekst,
        saksbehandler = utførtAv,
        // I noen tilfeller kan den være underkjent og da viser vi attestant fra attesteringen, selvom den kan endres senere.
        attestant = attesteringer.prøvHentSisteAttestering()?.attestant,
    )

    override fun lagBrevutkastCommandForAttestant(
        satsFactory: SatsFactory,
        utførtAv: NavIdentBruker.Attestant,
    ) = lagDokumentCommand(
        satsFactory = satsFactory,
        fritekst = fritekstTilBrev,
        saksbehandler = saksbehandler,
        attestant = utførtAv,
    )

    override fun lagBrevCommand(
        satsFactory: SatsFactory,
    ) = lagDokumentCommand(
        satsFactory = satsFactory,
        fritekst = fritekstTilBrev,
        saksbehandler = saksbehandler,
        attestant = attesteringer.hentSisteIverksatteAttesteringOrNull()!!.attestant,
    )

    private fun lagDokumentCommand(
        satsFactory: SatsFactory,
        saksbehandler: NavIdentBruker.Saksbehandler,
        attestant: NavIdentBruker.Attestant?,
        fritekst: String,
    ): IverksettSøknadsbehandlingDokumentCommand.Innvilgelse {
        val bosituasjon = grunnlagsdata.bosituasjon
        return IverksettSøknadsbehandlingDokumentCommand.Innvilgelse(
            beregning = beregning,
            harEktefelle = bosituasjon.harEPS(),
            // TODO jah: 1) vil ikke lenger fungere for alder. 2) kan kanskje dra ut i en mer generell funksjon.
            forventetInntektStørreEnn0 = vilkårsvurderinger.uføreVilkår()
                .getOrNull()!!.grunnlag.harForventetInntektStørreEnn0(),
            saksbehandler = saksbehandler,
            attestant = attestant,
            fritekst = fritekst,
            saksnummer = saksnummer,
            satsoversikt = Satsoversikt.fra(bosituasjon, satsFactory, sakstype),
            sakstype = sakstype,
            fødselsnummer = fnr,
        )
    }
}
