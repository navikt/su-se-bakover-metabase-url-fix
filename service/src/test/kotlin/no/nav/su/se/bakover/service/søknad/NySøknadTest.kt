package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.util.UUID

class NySøknadTest {

    private val søknadInnhold: SøknadInnhold = SøknadInnholdTestdataBuilder.build()
    private val fnr = søknadInnhold.personopplysninger.fnr
    private val person: Person = PersonOppslagStub.person(fnr).orNull()!!
    private val sakFactory: SakFactory = SakFactory()
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val sak: Sak = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = Tidspunkt.EPOCH,
        fnr = fnr,
        utbetalinger = emptyList()
    )
    private val pdf = "pdf-data".toByteArray()
    private val journalpostId = JournalpostId("1")
    private val oppgaveId = OppgaveId("2")

    @Test
    fun `Fant ikke person`() {
        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }
        val søknadRepoMock: SøknadRepo = mock()
        val sakServiceMock: SakService = mock()
        val pdfGeneratorMock: PdfGenerator = mock()
        val dokArkivMock: DokArkiv = mock()
        val oppgaveServiceMock: OppgaveService = mock()
        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = sakFactory,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock()
        )

        søknadService.nySøknad(søknadInnhold) shouldBe KunneIkkeOppretteSøknad.FantIkkePerson.left()
        verify(personOppslagMock).person(argThat { it shouldBe fnr })
        verifyNoMoreInteractions(
            personOppslagMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )
    }

    @Test
    fun `ny sak med søknad hvor pdf-generering feilet`() {
        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }
        val sakServiceMock: SakService = mock {
            on { hentSak(any<Fnr>()) } doReturn FantIkkeSak.left() doReturn sak.right()
            on { opprettSak(any()) }.doNothing()
        }

        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn ClientError(1, "").left()
        }
        val dokArkivMock: DokArkiv = mock()
        val søknadRepoMock: SøknadRepo = mock()
        val oppgaveServiceMock: OppgaveService = mock()
        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = sakFactory,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock()
        )

        val actual = søknadService.nySøknad(søknadInnhold)

        lateinit var expected: Søknad
        inOrder(
            personOppslagMock,
            sakServiceMock,
            pdfGeneratorMock
        ) {
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSak(argThat<Fnr> { it shouldBe fnr })
            verify(sakServiceMock).opprettSak(
                argThat {
                    it shouldBe NySak(
                        id = it.id,
                        opprettet = it.opprettet,
                        fnr = fnr,
                        søknad = Søknad.Ny(
                            id = it.søknad.id,
                            opprettet = it.søknad.opprettet,
                            sakId = it.id,
                            søknadInnhold = søknadInnhold,
                        ),
                    ).also { nySak ->
                        expected = nySak.søknad
                    }
                }
            )
            verify(sakServiceMock).hentSak(argThat<Fnr> { it shouldBe fnr })
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold(
                        saksnummer = sak.saksnummer,
                        søknadsId = it.søknadsId,
                        navn = person.navn,
                        søknadOpprettet = it.søknadOpprettet,
                        søknadInnhold = søknadInnhold,
                    )
                }
            )
        }
        verifyNoMoreInteractions(
            personOppslagMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )
        actual shouldBe Pair(sak.saksnummer, expected).right()
    }

    @Test
    fun `nye søknader bortsett fra den første skal ha en annerledes opprettet tidspunkt`() {
        val sak = sak.copy(
            søknader = listOf<Søknad>(
                Søknad.Ny(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.EPOCH,
                    sakId = sak.id,
                    søknadInnhold = søknadInnhold
                )
            )
        )
        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }
        val sakServiceMock: SakService = mock {
            on { hentSak(any<Fnr>()) } doReturn sak.right()
        }
        val søknadRepoMock: SøknadRepo = mock {
            on { opprettSøknad(any()) }.doNothing()
            on { oppdaterjournalpostId(any(), any()) }.doNothing()
            on { oppdaterOppgaveId(any(), any()) }.doNothing()
        }
        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
        }
        val dokArkivMock: DokArkiv = mock {
            on { opprettJournalpost(any()) } doReturn journalpostId.right()
        }

        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgave(any()) } doReturn oppgaveId.right()
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = sakFactory,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock()
        )

        val nySøknad = søknadService.nySøknad(søknadInnhold)

        inOrder(
            personOppslagMock,
            sakServiceMock,
            søknadRepoMock,
            pdfGeneratorMock,
            dokArkivMock
        ) {
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSak(argThat<Fnr> { it shouldBe fnr })
            verify(søknadRepoMock).opprettSøknad(
                argThat {
                    it shouldBe Søknad.Ny(
                        id = it.id,
                        opprettet = it.opprettet,
                        sakId = sakId,
                        søknadInnhold = søknadInnhold,
                    )
                }
            )
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold(
                        saksnummer = sak.saksnummer,
                        søknadsId = it.søknadsId,
                        navn = person.navn,
                        søknadOpprettet = it.søknadOpprettet,
                        søknadInnhold = søknadInnhold,
                    )
                }
            )
            verify(dokArkivMock).opprettJournalpost(
                argThat {
                    it shouldBe Journalpost.Søknadspost(
                        person = person,
                        saksnummer = saksnummer,
                        søknadInnhold = søknadInnhold,
                        pdf = pdf
                    )
                }
            )
        }

        nySøknad.map { (_, søknad) ->
            søknad.opprettet shouldNotBe sak.søknader().first().opprettet
        }
    }

    @Test
    fun `eksisterende sak med søknad hvor journalføring feiler`() {
        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }
        val sakServiceMock: SakService = mock {
            on { hentSak(any<Fnr>()) } doReturn sak.right()
        }
        val søknadRepoMock: SøknadRepo = mock {
            on { opprettSøknad(any()) }.doNothing()
        }
        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
        }
        val dokArkivMock: DokArkiv = mock {
            on { opprettJournalpost(any()) } doReturn ClientError(1, "").left()
        }

        val oppgaveServiceMock: OppgaveService = mock()

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = sakFactory,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock()
        )

        val actual = søknadService.nySøknad(søknadInnhold)

        lateinit var expectedSøknad: Søknad
        inOrder(
            personOppslagMock,
            sakServiceMock,
            søknadRepoMock,
            pdfGeneratorMock,
            dokArkivMock
        ) {
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSak(argThat<Fnr> { it shouldBe fnr })
            verify(søknadRepoMock).opprettSøknad(
                argThat {
                    it shouldBe Søknad.Ny(
                        id = it.id,
                        opprettet = it.opprettet,
                        sakId = sakId,
                        søknadInnhold = søknadInnhold,
                    ).also { søknad ->
                        expectedSøknad = søknad
                    }
                }
            )
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold(
                        saksnummer = sak.saksnummer,
                        søknadsId = it.søknadsId,
                        navn = person.navn,
                        søknadOpprettet = it.søknadOpprettet,
                        søknadInnhold = søknadInnhold,
                    )
                }
            )
            verify(dokArkivMock).opprettJournalpost(
                argThat {
                    it shouldBe Journalpost.Søknadspost(
                        person = person,
                        saksnummer = saksnummer,
                        søknadInnhold = søknadInnhold,
                        pdf = pdf
                    )
                }
            )
        }
        verifyNoMoreInteractions(
            personOppslagMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )

        actual shouldBe Pair(sak.saksnummer, expectedSøknad).right()
    }

    @Test
    fun `eksisterende sak med søknad hvor oppgave feiler`() {
        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }
        val sakServiceMock: SakService = mock {
            on { hentSak(any<Fnr>()) } doReturn sak.right()
        }
        val søknadRepoMock: SøknadRepo = mock {
            on { opprettSøknad(any()) }.doNothing()
            on { oppdaterjournalpostId(any(), any()) }.doNothing()
        }
        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
        }
        val dokArkivMock: DokArkiv = mock {
            on { opprettJournalpost(any()) } doReturn journalpostId.right()
        }

        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = sakFactory,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock()
        )

        val actual = søknadService.nySøknad(søknadInnhold)
        lateinit var expectedSøknad: Søknad
        inOrder(
            personOppslagMock,
            sakServiceMock,
            søknadRepoMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        ) {
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSak(argThat<Fnr> { it shouldBe fnr })
            verify(søknadRepoMock).opprettSøknad(
                argThat {
                    it shouldBe Søknad.Ny(
                        id = it.id,
                        opprettet = it.opprettet,
                        sakId = sakId,
                        søknadInnhold = søknadInnhold,
                    ).also { søknad ->
                        expectedSøknad = søknad
                    }
                }
            )
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold(
                        saksnummer = sak.saksnummer,
                        søknadsId = it.søknadsId,
                        navn = person.navn,
                        søknadOpprettet = it.søknadOpprettet,
                        søknadInnhold = søknadInnhold,
                    )
                }
            )
            verify(dokArkivMock).opprettJournalpost(
                argThat {
                    it shouldBe Journalpost.Søknadspost(
                        person = person,
                        saksnummer = saksnummer,
                        søknadInnhold = søknadInnhold,
                        pdf = pdf
                    )
                }
            )
            verify(søknadRepoMock).oppdaterjournalpostId(
                søknadId = argThat { it shouldBe expectedSøknad.id },
                journalpostId = argThat { it shouldBe journalpostId }
            )
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Saksbehandling(
                        journalpostId = journalpostId,
                        søknadId = expectedSøknad.id,
                        aktørId = person.ident.aktørId
                    )
                }
            )
        }
        verifyNoMoreInteractions(
            personOppslagMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )

        actual shouldBe Pair(sak.saksnummer, expectedSøknad).right()
    }

    @Test
    fun `eksisterende sak med søknad hvor oppgavekallet går bra`() {
        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }
        val sakServiceMock: SakService = mock {
            on { hentSak(any<Fnr>()) } doReturn sak.right()
        }
        val søknadRepoMock: SøknadRepo = mock {
            on { opprettSøknad(any()) }.doNothing()
            on { oppdaterjournalpostId(any(), any()) }.doNothing()
            on { oppdaterOppgaveId(any(), any()) }.doNothing()
        }
        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
        }
        val dokArkivMock: DokArkiv = mock {
            on { opprettJournalpost(any()) } doReturn journalpostId.right()
        }

        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgave(any()) } doReturn oppgaveId.right()
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = sakFactory,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock()
        )

        val actual = søknadService.nySøknad(søknadInnhold)
        lateinit var expectedSøknad: Søknad
        inOrder(
            personOppslagMock,
            sakServiceMock,
            søknadRepoMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        ) {
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSak(argThat<Fnr> { it shouldBe fnr })
            verify(søknadRepoMock).opprettSøknad(
                argThat {
                    it shouldBe Søknad.Ny(
                        id = it.id,
                        opprettet = it.opprettet,
                        sakId = sakId,
                        søknadInnhold = søknadInnhold,
                    ).also { søknad ->
                        expectedSøknad = søknad
                    }
                }
            )
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold(
                        saksnummer = sak.saksnummer,
                        søknadsId = it.søknadsId,
                        navn = person.navn,
                        søknadOpprettet = it.søknadOpprettet,
                        søknadInnhold = søknadInnhold,
                    )
                }
            )
            verify(dokArkivMock).opprettJournalpost(
                argThat {
                    it shouldBe Journalpost.Søknadspost(
                        person = person,
                        saksnummer = saksnummer,
                        søknadInnhold = søknadInnhold,
                        pdf = pdf
                    )
                }
            )
            verify(søknadRepoMock).oppdaterjournalpostId(
                søknadId = argThat { it shouldBe expectedSøknad.id },
                journalpostId = argThat { it shouldBe journalpostId }
            )
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Saksbehandling(
                        journalpostId = journalpostId,
                        søknadId = expectedSøknad.id,
                        aktørId = person.ident.aktørId
                    )
                }
            )
            verify(søknadRepoMock).oppdaterOppgaveId(
                søknadId = argThat { it shouldBe expectedSøknad.id },
                oppgaveId = argThat { it shouldBe oppgaveId }
            )
        }
        verifyNoMoreInteractions(
            personOppslagMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )

        actual shouldBe Pair(sak.saksnummer, expectedSøknad).right()
    }
}
