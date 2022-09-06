package no.nav.su.se.bakover.domain.brev.søknad.lukk

import arrow.core.Either
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.lagPersonalia
import no.nav.su.se.bakover.domain.dokument.Dokument
import java.time.LocalDate
import java.util.UUID

data class AvvistSøknadBrevRequest(
    override val person: Person,
    private val brevvalg: Brevvalg.SaksbehandlersValg.SkalSendeBrev,
    private val saksbehandlerNavn: String,
    override val dagensDato: LocalDate,
    override val saksnummer: Saksnummer,
) : LagBrevRequest {
    override val brevInnhold = when (brevvalg) {
        is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst -> AvvistSøknadFritekstBrevInnhold(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = brevvalg.fritekst,
        )
        is Brevvalg.SaksbehandlersValg.SkalSendeBrev.VedtaksbrevUtenFritekst -> AvvistSøknadVedtakBrevInnhold(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = null,
        )
    }

    override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<LagBrevRequest.KunneIkkeGenererePdf, ByteArray>): Either<LagBrevRequest.KunneIkkeGenererePdf, Dokument.UtenMetadata> {
        when (brevvalg) {
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst -> {
                return genererDokument(genererPdf).map {
                    Dokument.UtenMetadata.Informasjon.Annet(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(), // TODO jah: Ta inn clock
                        tittel = it.first,
                        generertDokument = it.second,
                        generertDokumentJson = it.third,
                    )
                }
            }
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.VedtaksbrevUtenFritekst -> {
                return genererDokument(genererPdf).map {
                    Dokument.UtenMetadata.Vedtak(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(), // TODO jah: Ta inn clock
                        tittel = it.first,
                        generertDokument = it.second,
                        generertDokumentJson = it.third,
                    )
                }
            }
        }
    }
}
