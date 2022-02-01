package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.vedtak.BehandlingType
import no.nav.su.se.bakover.domain.vedtak.VedtakType
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.time.LocalDate

class RegulerServiceImpl(
    private val vedtakService: VedtakService,
    private val sakService: SakService,
    private val clock: Clock,

) : ReguleringService {

    override fun hentAlleSakerSomKanReguleres(fraDato: LocalDate): Either<KanIkkeHenteSaker, SakerSomKanReguleres> {
        return SakerSomKanReguleres(
            saker = hentAlleSaker(fraDato),
        ).right()
    }

    // SØKNAD, tommel opp
    // AVSLAG,  filtrer bort
    // ENDRING, tommel opp
    // INGEN_ENDRING, manuell?
    // OPPHØR, ikke ta hensyn til de mnd som er opphørt
    // STANS_AV_YTELSE,   manuell
    // GJENOPPTAK_AV_YTELSE,  tommel opp
    // AVVIST_KLAGE,    filtrer bort

    private fun hentAlleSaker(fraDato: LocalDate): List<SakSomKanReguleres> {
        return vedtakService.hentListeOverSakidSomKanReguleres(fraDato)
            .filterNot { it.vedtakType == VedtakType.AVSLAG || it.vedtakType == VedtakType.AVVIST_KLAGE }
            .groupBy { it.sakId }
            .mapNotNull { (sakid, vedtakSomKanReguleres) ->
                val minFra: LocalDate = vedtakSomKanReguleres.minOf { it.fraOgMed }
                val maxTil: LocalDate = vedtakSomKanReguleres.maxOf { it.tilOgMed }

                val gjeldendeVedtakPrMnd = Periode.create(minFra, maxTil).tilMånedsperioder().map { mnd ->
                    mnd to vedtakSomKanReguleres.filter {
                        val vedtaksperiode = Periode.create(it.fraOgMed, it.tilOgMed)
                        vedtaksperiode.inneholder(mnd)
                    }.maxByOrNull { it.opprettet.instant }!!
                }.filterNot { it.second.vedtakType == VedtakType.OPPHØR }.ifEmpty {
                    return@mapNotNull null
                }

                val type =
                    if (gjeldendeVedtakPrMnd.any { it.second.behandlingType == BehandlingType.MANUELL || it.second.vedtakType == VedtakType.INGEN_ENDRING || it.second.vedtakType == VedtakType.STANS_AV_YTELSE }) {
                        BehandlingType.MANUELL
                    } else BehandlingType.AUTOMATISK

                SakSomKanReguleres(
                    sakId = sakid,
                    saksnummer = vedtakSomKanReguleres.first().saksnummer,
                    type = type.toString(),
                )
            }
    }
}
