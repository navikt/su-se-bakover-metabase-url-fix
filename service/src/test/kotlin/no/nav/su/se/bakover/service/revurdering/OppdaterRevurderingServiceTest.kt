package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.fixedLocalDate
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.sak
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.sakId
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.søknadsbehandlingVedtak
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.uføregrunnlag
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.vilkårsvurderinger
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

val oppgaveId = OppgaveId("oppgaveId")

internal class OppdaterRevurderingServiceTest {
    private val behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()

    private val opprettetFraOgMed = fixedLocalDate
    private val denFørsteInneværendeMåned = fixedLocalDate.let {
        LocalDate.of(
            it.year,
            it.month,
            1,
        )
    }
    private val nesteMåned =
        LocalDate.of(
            denFørsteInneværendeMåned.year,
            denFørsteInneværendeMåned.month.plus(1),
            1,
        )
    private val periode = Periode.create(
        fraOgMed = nesteMåned,
        tilOgMed = nesteMåned.let {
            val treMånederFramITid = it.plusMonths(3)
            LocalDate.of(
                treMånederFramITid.year,
                treMånederFramITid.month,
                treMånederFramITid.lengthOfMonth(),
            )
        },
    )
    private val saksbehandler = NavIdentBruker.Saksbehandler("Sak S. behandler")
    private val revurderingId = UUID.randomUUID()

    private val revurderingsårsak = Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
    )

    private val informasjonSomRevurderes = listOf(
        Revurderingsteg.Uførhet,
    )

    private val tilRevurdering = søknadsbehandlingVedtak.copy(periode = periode)
    private val opprettetRevurdering = OpprettetRevurdering(
        id = revurderingId,
        periode = periode,
        opprettet = fixedTidspunkt,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = null,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = Grunnlagsdata.EMPTY,
        vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        informasjonSomRevurderes = InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert)),
    )

    @Test
    fun `ugyldig begrunnelse`() {
        val mocks = RevurderingServiceMocks()
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = opprettetFraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigBegrunnelse.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ugyldig årsak`() {
        val mocks = RevurderingServiceMocks()
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = opprettetFraOgMed,
                årsak = "UGYLDIG_ÅRSAK",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigÅrsak.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Fant ikke revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn null
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = opprettetFraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.FantIkkeRevurdering.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Kan ikke oppdatere sendt forhåndsvarslet revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.copy(
                forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt(
                    journalpostId = JournalpostId("journalpostId"),
                    brevbestillingId = BrevbestillingId("brevbestillingId"),
                ),
            )
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed.plus(1, ChronoUnit.DAYS),
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.KanIkkeOppdatereRevurderingSomErForhåndsvarslet.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Kan ikke oppdatere besluttet forhåndsvarslet revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.copy(
                forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                    journalpostId = JournalpostId("journalpostId"),
                    brevbestillingId = BrevbestillingId("brevbestillingId"),
                    begrunnelse = "besluttetForhåndsvarslingBegrunnelse",
                    valg = BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
                ),
            )
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed.plus(1, ChronoUnit.DAYS),
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.KanIkkeOppdatereRevurderingSomErForhåndsvarslet.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `oppdatert periode må være fra neste kalendermåned`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed.minus(1, ChronoUnit.MONTHS),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.PeriodeOgÅrsakKombinasjonErUgyldig.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Ugyldig periode - fra og med dato må være første dag i måneden`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed.plus(1, ChronoUnit.DAYS),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden)
            .left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Ugyldig tilstand`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(opprettetRevurdering.sakId) } doReturn sak.right()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.let {
                IverksattRevurdering.Innvilget(
                    id = it.id,
                    periode = it.periode,
                    opprettet = it.opprettet,
                    tilRevurdering = it.tilRevurdering,
                    saksbehandler = it.saksbehandler,
                    oppgaveId = it.oppgaveId,
                    fritekstTilBrev = it.fritekstTilBrev,
                    revurderingsårsak = it.revurderingsårsak,
                    beregning = mock(),
                    attestering = Attestering.Iverksatt(
                        attestant = NavIdentBruker.Attestant("navIdent"),
                    ),
                    behandlingsinformasjon = behandlingsinformasjon,
                    simulering = mock(),
                    forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                    grunnlagsdata = Grunnlagsdata.EMPTY,
                    vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
                    informasjonSomRevurderes = it.informasjonSomRevurderes,
                )
            }
        }

        val mocks = RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
            sakService = sakServiceMock,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigTilstand(
            IverksattRevurdering.Innvilget::class,
            OpprettetRevurdering::class,
        ).left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        verify(sakServiceMock).hentSak(opprettetRevurdering.sakId)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `oppdater en revurdering`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(opprettetRevurdering.sakId) } doReturn sak.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }
        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()
        val grunnlagServiceMock = mock<GrunnlagService>()

        val mocks = RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            sakService = sakServiceMock,
            grunnlagService = grunnlagServiceMock,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        ).getOrHandle {
            throw RuntimeException("$it")
        }

        actual.let { oppdatertRevurdering ->
            oppdatertRevurdering.periode shouldBe periode
            oppdatertRevurdering.tilRevurdering shouldBe tilRevurdering
            oppdatertRevurdering.saksbehandler shouldBe saksbehandler
            oppdatertRevurdering.oppgaveId shouldBe OppgaveId("oppgaveId")
            oppdatertRevurdering.fritekstTilBrev shouldBe ""
            oppdatertRevurdering.revurderingsårsak shouldBe revurderingsårsak
            oppdatertRevurdering.forhåndsvarsel shouldBe null
            oppdatertRevurdering.behandlingsinformasjon shouldBe tilRevurdering.behandlingsinformasjon
            oppdatertRevurdering.grunnlagsdata.uføregrunnlag.let {
                it shouldHaveSize 1
                it[0].ekvivalentMed(uføregrunnlag)
            }
            oppdatertRevurdering.vilkårsvurderinger.uføre.ekvivalentMed(vilkårsvurderinger.uføre as Vilkår.Vurdert.Uførhet)
            oppdatertRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert))
        }

        inOrder(
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
            grunnlagServiceMock,
            sakServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(sakServiceMock).hentSak(sakId)
            verify(revurderingRepoMock).lagre(actual)
            verify(vilkårsvurderingServiceMock).lagre(actual.id, actual.vilkårsvurderinger)
            verify(grunnlagServiceMock).lagreFradragsgrunnlag(actual.id, actual.grunnlagsdata.fradragsgrunnlag)
        }
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `oppdatert periode for g-regulering kan være nåværende kalendermåned`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(opprettetRevurdering.sakId) } doReturn sak.right()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }
        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()
        val grunnlagServiceMock = mock<GrunnlagService>()

        val mocks = RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            sakService = sakServiceMock,
            grunnlagService = grunnlagServiceMock,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = fixedLocalDate.startOfMonth(),
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "g-regulering",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        ).getOrHandle { throw RuntimeException("$it") }

        actual.periode.fraOgMed shouldBe fixedLocalDate.startOfMonth()
        actual.revurderingsårsak.årsak shouldBe Revurderingsårsak.Årsak.REGULER_GRUNNBELØP
        actual.revurderingsårsak.begrunnelse.toString() shouldBe "g-regulering"

        inOrder(
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
            sakServiceMock,
            grunnlagServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(sakServiceMock).hentSak(sakId)
            verify(revurderingRepoMock).lagre(actual)
            verify(vilkårsvurderingServiceMock).lagre(actual.id, actual.vilkårsvurderinger)
            verify(grunnlagServiceMock).lagreFradragsgrunnlag(actual.id, actual.grunnlagsdata.fradragsgrunnlag)
        }
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `oppdatert periode for g-regulering kan være forrige kalendermåned`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(opprettetRevurdering.sakId) } doReturn sak.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }
        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()
        val grunnlagServiceMock = mock<GrunnlagService>()

        val mocks = RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            sakService = sakServiceMock,
            grunnlagService = grunnlagServiceMock,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = fixedLocalDate.minusMonths(1).startOfMonth(),
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "g-regulering",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        ).getOrHandle { throw RuntimeException("$it") }

        actual.periode.fraOgMed shouldBe fixedLocalDate.minusMonths(1).startOfMonth()
        actual.revurderingsårsak.årsak shouldBe Revurderingsårsak.Årsak.REGULER_GRUNNBELØP
        actual.revurderingsårsak.begrunnelse.toString() shouldBe "g-regulering"

        inOrder(
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
            sakServiceMock,
            grunnlagServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(sakServiceMock).hentSak(sakId)
            verify(revurderingRepoMock).lagre(actual)
            verify(vilkårsvurderingServiceMock).lagre(actual.id, actual.vilkårsvurderinger)
            verify(grunnlagServiceMock).lagreFradragsgrunnlag(actual.id, actual.grunnlagsdata.fradragsgrunnlag)
        }
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `må velge minst ting som skal revurderes`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = emptyList(),
            ),
        ) shouldBe KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes.left()
    }
}
