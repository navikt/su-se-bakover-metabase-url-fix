package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.util.UUID

internal class StartUtbetalingerServiceTest {

    @Test
    fun `Utbetalinger som er stanset blir startet igjen`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                lagUtbetaling(sak.id, Oppdrag.UtbetalingStrategy.Gjenoppta(behandler = attestant))
            } doReturn utbetalingForSimulering
            on {
                simulerUtbetaling(utbetalingForSimulering)
            } doReturn simulertUtbetaling.right()
            on {
                utbetal(argThat { it shouldBe simulertUtbetaling })
            } doReturn oversendtUtbetaling.right()
        }

        val response = StartUtbetalingerService(
            utbetalingService = utbetalingServiceMock,
            sakService = sakServiceMock
        ).startUtbetalinger(sak.id)

        response shouldBe sak.right()
        inOrder(
            sakServiceMock,
            utbetalingServiceMock,
            utbetalingServiceMock
        ) {
            verify(sakServiceMock, Times(1)).hentSak(sak.id)
            verify(utbetalingServiceMock, Times(1)).lagUtbetaling(sak.id, Oppdrag.UtbetalingStrategy.Gjenoppta(behandler = attestant))
            verify(utbetalingServiceMock, Times(1)).simulerUtbetaling(utbetalingForSimulering)
            verify(utbetalingServiceMock, Times(1)).utbetal(simulertUtbetaling)
        }
    }

    @Test
    fun `Fant ikke sak`() {
        val repoMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn FantIkkeSak.left()
        }
        val utbetalingServiceMock = mock<UtbetalingService>()
        val service = StartUtbetalingerService(
            utbetalingService = utbetalingServiceMock,
            sakService = repoMock
        )

        val response = service.startUtbetalinger(sak.id)
        response shouldBe StartUtbetalingFeilet.FantIkkeSak.left()

        verify(repoMock).hentSak(sak.id)
        verifyNoMoreInteractions(repoMock)
        verifyZeroInteractions(utbetalingServiceMock)
    }

    @Test
    fun `Simulering feilet`() {
        val repoMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                lagUtbetaling(sak.id, Oppdrag.UtbetalingStrategy.Gjenoppta(behandler = attestant))
            } doReturn utbetalingForSimulering
            on {
                simulerUtbetaling(utbetalingForSimulering)
            } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val response = StartUtbetalingerService(
            utbetalingService = utbetalingServiceMock,
            sakService = repoMock,
        ).startUtbetalinger(sak.id)

        response shouldBe StartUtbetalingFeilet.SimuleringAvStartutbetalingFeilet.left()

        inOrder(repoMock, utbetalingServiceMock) {
            verify(repoMock, Times(1)).hentSak(sak.id)
            verify(utbetalingServiceMock, Times(1)).lagUtbetaling(sak.id, Oppdrag.UtbetalingStrategy.Gjenoppta(behandler = attestant))
            verify(utbetalingServiceMock, Times(1)).simulerUtbetaling(utbetalingForSimulering)
        }
        verifyNoMoreInteractions(repoMock, utbetalingServiceMock)
    }

    @Test
    fun `Utbetaling feilet`() {
        val repoMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                lagUtbetaling(sak.id, Oppdrag.UtbetalingStrategy.Gjenoppta(behandler = attestant))
            } doReturn utbetalingForSimulering
            on {
                simulerUtbetaling(utbetalingForSimulering)
            } doReturn simulertUtbetaling.right()
            on {
                utbetal(argThat { it shouldBe simulertUtbetaling })
            } doReturn UtbetalingFeilet.left()
        }

        val response = StartUtbetalingerService(
            utbetalingService = utbetalingServiceMock,
            sakService = repoMock
        ).startUtbetalinger(sak.id)

        response shouldBe StartUtbetalingFeilet.SendingAvUtebetalingTilOppdragFeilet.left()

        inOrder(
            repoMock,
            utbetalingServiceMock
        ) {
            verify(repoMock, Times(1)).hentSak(sak.id)
            verify(utbetalingServiceMock, Times(1)).lagUtbetaling(sak.id, Oppdrag.UtbetalingStrategy.Gjenoppta(behandler = attestant))
            verify(utbetalingServiceMock, Times(1)).simulerUtbetaling(utbetalingForSimulering)
            verify(utbetalingServiceMock, Times(1)).utbetal(simulertUtbetaling)
        }
    }

    private val fnr = Fnr("12345678910")
    private val sakId = UUID.randomUUID()
    private val attestant = NavIdentBruker.Attestant("SU")
    private val avstemmingsnøkkel = Avstemmingsnøkkel()
    private val oppdrag: Oppdrag = Oppdrag(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = sakId,
        utbetalinger = emptyList()
    )

    private val sak: Sak = Sak(
        id = sakId,
        opprettet = Tidspunkt.now(),
        fnr = fnr,
        oppdrag = oppdrag
    )

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        fnr = fnr,
        utbetalingslinjer = listOf(),
        type = Utbetaling.UtbetalingsType.GJENOPPTA,
        oppdragId = oppdrag.id,
        behandler = attestant
    )

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = idag(),
        nettoBeløp = 13,
        periodeList = listOf()
    )
    private val oppdragsmelding = Oppdragsmelding(
        originalMelding = "",
        avstemmingsnøkkel = avstemmingsnøkkel
    )
    private val simulertUtbetaling = utbetalingForSimulering.toSimulertUtbetaling(simulering)
    private val oversendtUtbetaling = simulertUtbetaling.toOversendtUtbetaling(oppdragsmelding)
}
