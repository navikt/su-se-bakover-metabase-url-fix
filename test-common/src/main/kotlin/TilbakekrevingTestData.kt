package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
import java.math.BigDecimal
import java.time.Clock

/**
 * TODO dobbeltimplementasjon
 * @see [no.nav.su.se.bakover.web.services.tilbakekreving.matchendeKravgrunnlag]
 */
fun matchendeKravgrunnlag(
    revurdering: Revurdering,
    simulering: Simulering,
    utbetalingId: UUID30,
    clock: Clock,
): Kravgrunnlag {
    val now = Tidspunkt.now(clock)
    return simulering.let {
        Kravgrunnlag(
            saksnummer = revurdering.saksnummer,
            eksternKravgrunnlagId = "123456",
            eksternVedtakId = "654321",
            eksternKontrollfelt = now.toOppdragTimestamp(),
            status = Kravgrunnlag.KravgrunnlagStatus.Nytt,
            behandler = "K231B433",
            utbetalingId = utbetalingId,
            eksternTidspunkt = now,
            grunnlagsmåneder = it.hentFeilutbetalteBeløp()
                .map { (periode, feilutbetaling) ->
                    Kravgrunnlag.Grunnlagsmåned(
                        måned = periode.tilMåned(),
                        betaltSkattForYtelsesgruppen = BigDecimal(4395),
                        grunnlagsbeløp = listOf(
                            Kravgrunnlag.Grunnlagsmåned.Grunnlagsbeløp(
                                kode = KlasseKode.KL_KODE_FEIL_INNT,
                                type = KlasseType.FEIL,
                                beløpTidligereUtbetaling = BigDecimal.ZERO,
                                beløpNyUtbetaling = BigDecimal(feilutbetaling.sum()),
                                beløpSkalTilbakekreves = BigDecimal.ZERO,
                                beløpSkalIkkeTilbakekreves = BigDecimal.ZERO,
                                skatteProsent = BigDecimal.ZERO,
                            ),
                            Kravgrunnlag.Grunnlagsmåned.Grunnlagsbeløp(
                                kode = KlasseKode.SUUFORE,
                                type = KlasseType.YTEL,
                                beløpTidligereUtbetaling = BigDecimal(it.hentUtbetalteBeløp(periode)!!.sum()),
                                beløpNyUtbetaling = BigDecimal(it.hentTotalUtbetaling(periode)!!.sum()),
                                beløpSkalTilbakekreves = BigDecimal(feilutbetaling.sum()),
                                beløpSkalIkkeTilbakekreves = BigDecimal.ZERO,
                                skatteProsent = BigDecimal("43.9983"),
                            ),
                        ),
                    )
                },
        )
    }
}
