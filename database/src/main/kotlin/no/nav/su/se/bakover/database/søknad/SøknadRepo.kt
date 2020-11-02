package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

interface SøknadRepo {
    fun hentSøknad(søknadId: UUID): Søknad?
    fun opprettSøknad(søknad: Søknad)
    fun lukkSøknad(søknadId: UUID, lukket: Søknad.Lukket)
    fun harSøknadPåbegyntBehandling(søknadId: UUID): Boolean
    fun oppdaterjournalpostId(søknadId: UUID, journalpostId: JournalpostId)
    fun oppdaterOppgaveId(søknadId: UUID, oppgaveId: OppgaveId)
    fun hentOppgaveId(søknadId: UUID): OppgaveId?
}
