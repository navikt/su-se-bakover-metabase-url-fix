package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.Behandlingsperiode
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingsinformasjon
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class SøknadsbehandlingServiceOppdaterBehandlingsperiodeTest {

    private val sakId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val saksbehandler = Saksbehandler("AB12345")
    private val oppgaveId = OppgaveId("o")
    private val behandlingsperiode = Behandlingsperiode(Periode.create(1.januar(2021), 31.desember(2021)))

    @Test
    fun `svarer med feil hvis man ikke finner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).oppdaterBehandlingsperiode(
            SøknadsbehandlingService.OppdaterBehandlingsperiodeRequest(behandlingId, behandlingsperiode),
        )

        response shouldBe SøknadsbehandlingService.KunneIkkeOppdatereBehandlingsperiode.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `kaster exception ved hvs søknadsbehandling er i ugyldig tilstand for oppdatering av behandlingsperiode`() {
        val tilAttestering = Søknadsbehandling.Vilkårsvurdert.Avslag(
            id = behandlingId,
            opprettet = Tidspunkt.now(),
            behandlingsinformasjon = behandlingsinformasjon,
            søknad = Søknad.Journalført.MedOppgave(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = oppgaveId,
                journalpostId = JournalpostId("j"),
            ),
            sakId = sakId,
            saksnummer = Saksnummer(0),
            fnr = FnrGenerator.random(),
            oppgaveId = oppgaveId,
            fritekstTilBrev = "",
            behandlingsperiode = behandlingsperiode,
        ).tilAttestering(Saksbehandler("saksa"), "")

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn tilAttestering
        }

        assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
            createSøknadsbehandlingService(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            ).oppdaterBehandlingsperiode(
                SøknadsbehandlingService.OppdaterBehandlingsperiodeRequest(behandlingId, behandlingsperiode),
            )
        }
    }

    @Test
    fun `happy case`() {
        val uavklart = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = behandlingId,
            opprettet = Tidspunkt.now(),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            søknad = Søknad.Journalført.MedOppgave(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = oppgaveId,
                journalpostId = JournalpostId("j"),
            ),
            sakId = sakId,
            saksnummer = Saksnummer(0),
            fnr = FnrGenerator.random(),
            oppgaveId = oppgaveId,
            fritekstTilBrev = "",
            behandlingsperiode = null,
        )
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn uavklart
        }

        val expected = uavklart.copy(
            behandlingsperiode = behandlingsperiode,
        )

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).oppdaterBehandlingsperiode(
            SøknadsbehandlingService.OppdaterBehandlingsperiodeRequest(behandlingId, behandlingsperiode),
        )

        response shouldBe expected.right()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verify(søknadsbehandlingRepoMock).lagre(argThat { it shouldBe expected })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }
}
