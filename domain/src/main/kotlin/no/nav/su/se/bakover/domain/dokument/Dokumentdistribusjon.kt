package no.nav.su.se.bakover.domain.dokument

import arrow.core.Either
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.util.UUID

data class Dokumentdistribusjon(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = Tidspunkt.now(),
    val endret: Tidspunkt = opprettet,
    val dokument: Dokument.MedMetadata,
    val journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon,
) {
    fun journalfør(journalfør: () -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre, Dokumentdistribusjon> {
        return journalføringOgBrevdistribusjon.journalfør(journalfør)
            .map { copy(journalføringOgBrevdistribusjon = it) }
    }

    fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, Dokumentdistribusjon> {
        return journalføringOgBrevdistribusjon.distribuerBrev(distribuerBrev)
            .map { copy(journalføringOgBrevdistribusjon = it) }
    }
}
