package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.time.LocalDate
import kotlin.math.roundToInt

enum class Sats(val grunnbeløp: Grunnbeløp) {
    ORDINÆR(Grunnbeløp.`2,28G`),
    HØY(Grunnbeløp.`2,48G`);

    fun årsbeløp(dato: LocalDate): Double = satsSomÅrsbeløp(dato)

    fun månedsbeløp(dato: LocalDate): Double = satsSomMånedsbeløp(dato)

    fun månedsbeløpSomHeltall(dato: LocalDate): Int = satsSomMånedsbeløp(dato).roundToInt()

    fun datoForSisteEndringAvSats(dato: LocalDate): LocalDate = grunnbeløp.datoForSisteEndringAvGrunnbeløp(dato)

    fun periodiser(periode: Periode): Map<Periode, Double> {
        return periode.tilMånedsperioder().associateWith { satsSomMånedsbeløp(it.fraOgMed) }
    }

    private fun satsSomÅrsbeløp(dato: LocalDate): Double = grunnbeløp.fraDato(dato)

    private fun satsSomMånedsbeløp(dato: LocalDate): Double = grunnbeløp.fraDato(dato) / 12

    companion object {
        fun toProsentAvHøy(periode: Periode): Double = periode.tilMånedsperioder()
            .sumOf { HØY.månedsbeløp(it.fraOgMed) * 0.02 }

        fun Grunnlag.Bosituasjon.utledSats(): Sats {
            return when (this) {
                is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> ORDINÆR
                is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> ORDINÆR
                is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> HØY
                is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> ORDINÆR
                is Grunnlag.Bosituasjon.Fullstendig.Enslig -> HØY
                is Grunnlag.Bosituasjon.Ufullstendig.HarEpsIkkeValgtUførFlyktning -> throw IllegalStateException("Kan ikke utlede sats når man ikke har valgt om eps er ufør flyktning eller ikke")
                is Grunnlag.Bosituasjon.Ufullstendig.HarValgtEPSIkkeValgtEnsligVoksne -> throw IllegalStateException("Kan ikke utlede sats når man ikke har valgt bor alene eller med voksne")
            }
        }
    }
}
