package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class UtbetalingsstrategiStansTest {

    private val fixedClock = Clock.fixed(15.juni(2020).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val fnr = Fnr("12345678910")
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)

    @Test
    fun `stans av utbetaling`() {
        val utbetalingslinje = Utbetalingslinje.Ny(
            opprettet = fixedTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1500,
        )
        val utbetaling = createUtbetaling(
            nonEmptyListOf(
                utbetalingslinje,
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )

        val stans = Utbetalingsstrategi.Stans(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(utbetaling),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 1.juli(2020),
            clock = fixedClock,
        ).generate()

        stans.utbetalingslinjer[0] shouldBe Utbetalingslinje.Endring.Stans(
            id = utbetalingslinje.id,
            opprettet = stans.utbetalingslinjer[0].opprettet,
            fraOgMed = utbetalingslinje.fraOgMed,
            tilOgMed = utbetalingslinje.tilOgMed,
            forrigeUtbetalingslinjeId = utbetalingslinje.forrigeUtbetalingslinjeId,
            beløp = utbetalingslinje.beløp,
            virkningstidspunkt = 1.juli(2020),
        )
    }

    @Test
    fun `ingen løpende utbetalinger å stanse etter første dato i neste måned`() {
        val utbetaling = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.mai(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 1500,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )

        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Stans(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(utbetaling),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                stansDato = 1.juli(2020),
                clock = fixedClock,
            ).generate()
        }.also {
            it.message shouldContain "${1.juli(2020)}"
        }
    }

    @Test
    fun `siste utbetaling er en 'stans utbetaling'`() {
        val utbetaling = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 0,
                ),
            ),
            type = Utbetaling.UtbetalingsType.STANS,
        )

        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Stans(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(utbetaling),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                stansDato = 1.juli(2020),
                clock = fixedClock,
            ).generate()
        }.also {
            it.message shouldContain "allerede er stanset"
        }
    }

    @Test
    fun `det er ikke lov å stanse en utbetaling som allerede er opphørt`() {
        val utbetaling = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 0,
                ),
            ),
            type = Utbetaling.UtbetalingsType.OPPHØR,
        )

        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Stans(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(utbetaling),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                stansDato = 1.juli(2020),
                clock = fixedClock,
            ).generate()
        }.also {
            it.message shouldContain "Kan ikke stanse utbetalinger som allerede er opphørt"
        }
    }

    @Test
    fun `har utbetalinger senere enn stansdato`() {
        val utbetaling = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )

        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Stans(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(utbetaling),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                stansDato = 1.juli(2021),
                clock = fixedClock,
            ).generate()
        }.also {
            it.message shouldContain "Det eksisterer ingen utbetalinger med tilOgMed dato større enn eller lik"
        }
    }

    @Test
    fun `stansdato må være den første i måneden`() {
        val utbetaling = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )

        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Stans(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(utbetaling),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                stansDato = 10.juli(2020),
                clock = fixedClock,
            ).generate()
        }.also {
            it.message shouldContain "Dato for stans må være første dag i måneden"
        }
    }

    @Test
    fun `stansdato må være den første i neste måned`() {
        val utbetaling = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )

        listOf(
            1.juli(2020),
        ).forEach {
            Utbetalingsstrategi.Stans(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(utbetaling),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                stansDato = it,
                clock = fixedClock,
            ).generate().also {
                it should beOfType<Utbetaling.UtbetalingForSimulering>()
            }
        }
    }

    @Test
    fun `kaster exception dersom det eksisterer tidligere opphør i perioden mellom stansdato og nyeste utbetaling`() {
        val fixedClock15Juli21 = Clock.fixed(15.juli(2021).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

        val første = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = Tidspunkt.now(),
                    fraOgMed = 1.august(2021),
                    tilOgMed = 30.april(2022),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )
        val opphør = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = første.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.august(2021),
                    clock = Clock.systemUTC(),
                ),
            ),
            type = Utbetaling.UtbetalingsType.OPPHØR,
        )
        val andre = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = Tidspunkt.now(),
                    fraOgMed = 1.november(2021),
                    tilOgMed = 30.april(2022),
                    forrigeUtbetalingslinjeId = opphør.sisteUtbetalingslinje().id,
                    beløp = 10000,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )

        assertThrows<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Stans(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(første, opphør, andre),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                stansDato = 1.august(2021),
                clock = fixedClock15Juli21,
            ).generate()
        }
    }

    @Test
    fun `kaster exception dersom det eksisterer fremtidige opphør i perioden mellom stansdato og nyeste utbetaling`() {
        val fixedClock15Juli21 = Clock.fixed(15.juli(2021).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

        val første = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.august(2021),
                    tilOgMed = 30.april(2022),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )
        val andre = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.november(2021),
                    tilOgMed = 30.april(2022),
                    forrigeUtbetalingslinjeId = første.sisteUtbetalingslinje().id,
                    beløp = 10000,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )
        val opphør = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = andre.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.mars(2022),
                    clock = Clock.systemUTC(),
                ),
            ),
            type = Utbetaling.UtbetalingsType.OPPHØR,
        )

        assertThrows<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Stans(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(første, andre, opphør),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                stansDato = 1.januar(2022),
                clock = fixedClock15Juli21,
            ).generate()
        }
    }

    private fun createUtbetaling(utbetalingslinjer: NonEmptyList<Utbetalingslinje>, type: Utbetaling.UtbetalingsType) =
        Utbetaling.OversendtUtbetaling.MedKvittering(
            sakId = sakId,
            saksnummer = saksnummer,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            utbetalingsrequest = Utbetalingsrequest(
                value = "",
            ),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK_MED_VARSEL, ""),
            utbetalingslinjer = utbetalingslinjer,
            fnr = fnr,
            type = type,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
        )
}
