package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.dokument.Dokument

object JournalpostFactory {
    fun lagJournalpost(
        person: Person,
        saksnummer: Saksnummer,
        brevInnhold: BrevInnhold,
        pdf: ByteArray,
    ): Journalpost = when (brevInnhold.brevTemplate) {
        BrevTemplate.InnvilgetVedtak,
        BrevTemplate.AvslagsVedtak,
        BrevTemplate.AvvistSøknadVedtak,
        BrevTemplate.Opphørsvedtak,
        BrevTemplate.Revurdering.Inntekt,
        BrevTemplate.VedtakIngenEndring,
        -> Journalpost.Vedtakspost.from(person, saksnummer, brevInnhold, pdf)
        BrevTemplate.TrukketSøknad,
        BrevTemplate.AvvistSøknadFritekst,
        BrevTemplate.Forhåndsvarsel,
        -> Journalpost.Info.from(person, saksnummer, brevInnhold, pdf)
    }

    fun lagJournalpost(
        person: Person,
        saksnummer: Saksnummer,
        dokument: Dokument,
    ): Journalpost = when (dokument) {
        is Dokument.Informasjon,
        -> Journalpost.Info.from(person, saksnummer, dokument)
        is Dokument.Vedtak,
        -> Journalpost.Vedtakspost.from(person, saksnummer, dokument)
    }
}
