package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.domain.kontrollsamtale.UgyldigStatusovergang
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.kontrollsamtale.AnnulerKontrollsamtaleResultat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.nyUtbetalingOversendUtenKvittering
import no.nav.su.se.bakover.test.planlagtKontrollsamtale
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.simulerOpphør
import no.nav.su.se.bakover.test.simulerUtbetaling
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

internal class IverksettRevurderingTest {

    @Test
    fun `iverksett - iverksetter endring av ytelse`() {
        val clock = tikkendeFixedClock
        val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
        )
        val grunnlagsdata = grunnlagsdataEnsligMedFradrag().let { it.fradragsgrunnlag + it.bosituasjon }

        val (sak, revurderingTilAttestering) = revurderingTilAttestering(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            clock = clock,
            grunnlagsdataOverrides = grunnlagsdata,
        )

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = nyUtbetalingOversendUtenKvittering(
                sakOgBehandling = sak to revurderingTilAttestering,
                beregning = revurderingTilAttestering.beregning,
                clock = clock,
            ),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetalingsRequest.right()
            },
        )

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        sak,
                        invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        invocation.getArgument(1) as Periode,
                        clock,
                    )
                }.whenever(it).simulerUtbetaling(any(), any())
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = clock,
        )

        val response = serviceAndMocks.revurderingService.iverksett(revurderingTilAttestering.id, attestant)
            .getOrFail() as IverksattRevurdering.Innvilget

        verify(serviceAndMocks.sakService).hentSakForRevurdering(argThat { it shouldBe revurderingTilAttestering.id })
        verify(serviceAndMocks.utbetalingService, times(2)).simulerUtbetaling(any(), any())
        verify(serviceAndMocks.utbetalingService).klargjørUtbetaling(
            utbetaling = any(),
            transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.vedtakRepo).lagreITransaksjon(
            vedtak = argThat { it should beOfType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>() },
            sessionContext = argThat { TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.revurderingRepo).lagre(
            revurdering = argThat { it shouldBe response },
            transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(utbetalingKlargjortForOversendelse.callback).invoke(utbetalingsRequest)

        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `iverksett - iverksetter opphør av ytelse`() {
        val (sak, revurdering) = revurderingTilAttestering(
            clock = tikkendeFixedClock,
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).let { (sak, revurdering) -> sak to revurdering as RevurderingTilAttestering.Opphørt }

        val simulertUtbetaling = simulerOpphør(
            sak = sak,
            revurdering = revurdering,
            clock = tikkendeFixedClock,
        ).getOrFail()

        val callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
            on { it.invoke(any()) } doReturn utbetalingsRequest.right()
        }

        val utbetalingKlarForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = simulertUtbetaling.toOversendtUtbetaling(utbetalingsRequest),
            callback = callback,
        )

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any()) } doReturn simulertUtbetaling.right()
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlarForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            kontrollsamtaleService = mock {
                on {
                    annullerKontrollsamtale(
                        any(),
                        anyOrNull(),
                    )
                } doReturn AnnulerKontrollsamtaleResultat.AnnulertKontrollsamtale(planlagtKontrollsamtale()).right()
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = tikkendeFixedClock,
        )

        serviceAndMocks.revurderingService.iverksett(revurdering.id, attestant)

        verify(serviceAndMocks.sakService).hentSakForRevurdering(revurdering.id)
        verify(serviceAndMocks.utbetalingService, times(2)).simulerUtbetaling(
            any(),
            argThat { it shouldBe revurdering.periode },
        )
        verify(serviceAndMocks.utbetalingService).klargjørUtbetaling(
            utbetaling = any(),
            transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.vedtakRepo).lagreITransaksjon(
            vedtak = argThat { it should beOfType<VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering>() },
            sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.kontrollsamtaleService).annullerKontrollsamtale(
            sakId = argThat { it shouldBe revurdering.sakId },
            sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.revurderingRepo).lagre(
            revurdering = any(),
            transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(callback).invoke(utbetalingKlarForOversendelse.utbetaling.utbetalingsrequest)
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `iverksett opphør - opphøret skal publiseres etter alle databasekallene`() {
        val (sak, revurderingTilAttestering) = revurderingTilAttestering(
            clock = tikkendeFixedClock,
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).let { (sak, revurdering) -> sak to revurdering as RevurderingTilAttestering.Opphørt }

        val simulertUtbetaling = simulerOpphør(
            sak = sak,
            revurdering = revurderingTilAttestering,
            clock = tikkendeFixedClock,
        ).getOrFail()

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = simulertUtbetaling.toOversendtUtbetaling(utbetalingsRequest),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetalingsRequest.right()
            },
        )

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any()) } doReturn simulertUtbetaling.right()
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            kontrollsamtaleService = mock {
                on {
                    annullerKontrollsamtale(
                        any(),
                        anyOrNull(),
                    )
                } doReturn AnnulerKontrollsamtaleResultat.AnnulertKontrollsamtale(
                    planlagtKontrollsamtale(),
                ).right()
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = tikkendeFixedClock,
        )

        serviceAndMocks.revurderingService.iverksett(revurderingTilAttestering.id, attestant)

        inOrder(*serviceAndMocks.all(), utbetalingKlargjortForOversendelse.callback) {
            verify(serviceAndMocks.utbetalingService, times(2)).simulerUtbetaling(
                any(),
                argThat { it shouldBe revurderingTilAttestering.periode },
            )
            verify(serviceAndMocks.utbetalingService).klargjørUtbetaling(
                any(),
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.vedtakRepo).lagreITransaksjon(
                any(),
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.kontrollsamtaleService).annullerKontrollsamtale(
                any(),
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.revurderingRepo).lagre(
                any(),
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(utbetalingKlargjortForOversendelse.callback).invoke(utbetalingsRequest)
        }
    }

    @Test
    fun `iverksett innvilget - utbetaling skal publiseres etter alle databasekallene`() {
        val clock = tikkendeFixedClock
        val (sak, revurderingTilAttestering) = revurderingTilAttestering(
            clock = clock,
        )

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = nyUtbetalingOversendUtenKvittering(
                sakOgBehandling = sak to revurderingTilAttestering,
                beregning = revurderingTilAttestering.beregning,
                clock = clock,
            ),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetalingsRequest.right()
            },
        )

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        sak,
                        invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        invocation.getArgument(1) as Periode,
                    )
                }.whenever(it).simulerUtbetaling(any(), any())
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = clock,
        )

        serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingTilAttestering.id,
            attestant = attestant,
        )

        inOrder(*serviceAndMocks.all(), utbetalingKlargjortForOversendelse.callback) {
            verify(serviceAndMocks.vedtakRepo).lagreITransaksjon(
                vedtak = any(),
                sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.revurderingRepo).lagre(
                revurdering = any(),
                transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(utbetalingKlargjortForOversendelse.callback).invoke(utbetalingsRequest)
        }
    }

    @Test
    fun `skal returnere left om revurdering ikke er av typen RevurderingTilAttestering`() {
        val (sak, iverksatt) = iverksattRevurdering()
        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = iverksatt.id,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.FeilVedIverksettelse(
            no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeIverksetteRevurdering.UgyldigTilstand(
                iverksattRevurdering().second::class,
                IverksattRevurdering::class,
            ),
        ).left()
    }

    @Test
    fun `skal returnere left dersom lagring feiler for innvilget`() {
        val (sak, revurdering) = revurderingTilAttestering()
        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            vedtakRepo = mock {
                on { lagreITransaksjon(any(), anyOrNull()) } doThrow RuntimeException("Lagring feilet")
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any()) } doReturn simulerUtbetaling(
                    sak = sak,
                    revurdering = revurdering,
                ).getOrFail().right()
            },
            clock = tikkendeFixedClock,
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurdering.id,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet(no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon.LagringFeilet).left()
    }

    @Test
    fun `skal returnere left dersom lagring feiler for opphørt`() {
        val (sak, revurdering) = revurderingTilAttestering(
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        )
        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            vedtakRepo = mock {
                on { lagreITransaksjon(any(), anyOrNull()) } doThrow RuntimeException("Lagring feilet")
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any()) } doReturn simulerOpphør(
                    sak = sak,
                    revurdering = revurdering,
                ).getOrFail().right()
            },
            clock = tikkendeFixedClock,
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurdering.id,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet(no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon.LagringFeilet).left()
    }

    @Test
    fun `skal returnere left dersom utbetaling feiler for opphørt`() {
        val (sak, revurderingTilAttestering) = revurderingTilAttestering(
            clock = tikkendeFixedClock,
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).let { (sak, revurdering) -> sak to revurdering as RevurderingTilAttestering.Opphørt }

        val simulertUtbetaling = simulerOpphør(
            sak = sak,
            revurdering = revurderingTilAttestering,
            clock = tikkendeFixedClock,
        ).getOrFail()

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = simulertUtbetaling.toOversendtUtbetaling(utbetalingsRequest),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
            },
        )

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any()) } doReturn simulertUtbetaling.right()
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            kontrollsamtaleService = mock {
                on {
                    annullerKontrollsamtale(
                        any(),
                        anyOrNull(),
                    )
                } doReturn AnnulerKontrollsamtaleResultat.AnnulertKontrollsamtale(
                    planlagtKontrollsamtale(),
                ).right()
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = tikkendeFixedClock,
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingTilAttestering.id,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet(no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil)).left()
    }

    @Test
    fun `skal ikke opphøre dersom annullering av kontrollsamtale feiler`() {
        val (sak, revurderingTilAttestering) = revurderingTilAttestering(
            clock = tikkendeFixedClock,
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).let { (sak, revurdering) -> sak to revurdering as RevurderingTilAttestering.Opphørt }

        val simulertUtbetaling = simulerOpphør(
            sak = sak,
            revurdering = revurderingTilAttestering,
            clock = tikkendeFixedClock,
        ).getOrFail()

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = simulertUtbetaling.toOversendtUtbetaling(utbetalingsRequest),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
            },
        )
        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any()) } doReturn simulertUtbetaling.right()
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            kontrollsamtaleService = mock {
                on {
                    annullerKontrollsamtale(
                        any(),
                        anyOrNull(),
                    )
                } doReturn UgyldigStatusovergang.left()
                on { defaultSessionContext() } doReturn TestSessionFactory.sessionContext
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = tikkendeFixedClock,
        )

        serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingTilAttestering.id,
            attestant = attestant,
        ) shouldBe KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet(no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon.Opphør.KunneIkkeAnnullereKontrollsamtale).left()
    }

    @Test
    fun `skal returnere left dersom utbetaling feiler for iverksett`() {
        val clock = tikkendeFixedClock
        val (sak, revurderingTilAttestering) = revurderingTilAttestering(
            clock = clock,
        )

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = nyUtbetalingOversendUtenKvittering(
                sakOgBehandling = sak to revurderingTilAttestering,
                beregning = revurderingTilAttestering.beregning,
                clock = clock,
            ),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
            },
        )

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        sak,
                        invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        invocation.getArgument(1) as Periode,
                    )
                }.whenever(it).simulerUtbetaling(any(), any())
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            kontrollsamtaleService = mock {
                on {
                    annullerKontrollsamtale(
                        any(),
                        anyOrNull(),
                    )
                } doReturn AnnulerKontrollsamtaleResultat.AnnulertKontrollsamtale(
                    planlagtKontrollsamtale(),
                ).right()
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = clock,
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingTilAttestering.id,
            attestant = attestant,
        )
        response shouldBe KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet(no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil)).left()
    }

    @Test
    fun `feil ved åpent kravgrunnlag`() {
        val (sak, vedtakAvventerKravgrunnlag) = vedtakRevurdering(
            revurderingsperiode = mai(2021)..desember(2021),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = mai(2021)..desember(2021),
                    arbeidsinntekt = 5000.0,
                ),
            ),
            utbetalingerKjørtTilOgMed = 1.november(2021),
        )

        val (sakMedTilAttestering, revurderingTilAttestering) = revurderingTilAttestering(
            sakOgVedtakSomKanRevurderes = sak to vedtakAvventerKravgrunnlag,
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sakMedTilAttestering
            },
        ).also {
            it.revurderingService.iverksett(
                revurderingTilAttestering.id,
                attestant,
            ) shouldBe KunneIkkeIverksetteRevurdering.FeilVedIverksettelse(no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeIverksetteRevurdering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving).left()
        }
    }
}
