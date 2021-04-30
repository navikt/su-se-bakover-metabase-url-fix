package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Garantipensjonsnivå
import no.nav.su.se.bakover.domain.beregning.Sats

enum class FradragStrategyName {
    Enslig,
    EpsOver67År,
    EpsUnder67ÅrOgUførFlyktning,
    EpsUnder67År
}

fun getEpsFribeløp(strategyName: FradragStrategyName, periode: Periode): Double {
    return FradragStrategy.fromName(strategyName).getEpsFribeløp(periode)
}

sealed class FradragStrategy(private val name: FradragStrategyName) {
    fun getName() = name

    fun beregn(fradrag: List<Fradrag>, beregningsperiode: Periode): Map<Periode, List<Fradrag>> {
        val periodiserteFradrag = fradrag
            .flatMap { FradragFactory.periodiser(it) }
            .groupBy { it.periode }
        val beregningsperiodeMedFradrag =
            beregningsperiode.tilMånedsperioder().associateWith { (periodiserteFradrag[it] ?: emptyList()) }

        validate(beregningsperiodeMedFradrag)
        return beregnFradrag(beregningsperiodeMedFradrag)
    }

    protected open fun validate(fradrag: Map<Periode, List<Fradrag>>) {
        require(fradrag.values.all { it.`har nøyaktig en forventet inntekt for bruker`() }) { "Hele beregningsperioden må inneholde fradrag for brukers forventede inntekt etter uførhet." }
    }

    protected abstract fun beregnFradrag(fradrag: Map<Periode, List<Fradrag>>): Map<Periode, List<Fradrag>>

    abstract fun getEpsFribeløp(periode: Periode): Double

    object Enslig : FradragStrategy(FradragStrategyName.Enslig) {
        override fun beregnFradrag(fradrag: Map<Periode, List<Fradrag>>): Map<Periode, List<Fradrag>> {
            return fradrag.mapValues { it.value.filter { fradrag -> fradrag.getTilhører() == FradragTilhører.BRUKER } }
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
        }

        override fun getEpsFribeløp(periode: Periode): Double = 0.0
    }

    object EpsOver67År : FradragStrategy(FradragStrategyName.EpsOver67År) {
        override fun beregnFradrag(fradrag: Map<Periode, List<Fradrag>>): Map<Periode, List<Fradrag>> {
            return fradrag
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                .`fjern EPS fradrag opp til garantipensjonsnivå`()
        }

        override fun getEpsFribeløp(periode: Periode): Double = periodisertSumGarantipensjonsnivå(periode)

        private fun periodisertSumGarantipensjonsnivå(periode: Periode) =
            Garantipensjonsnivå.Ordinær.periodiser(periode).values.sumByDouble { it }

        private fun Map<Periode, List<Fradrag>>.`fjern EPS fradrag opp til garantipensjonsnivå`(): Map<Periode, List<Fradrag>> {
            return mapValues {
                `fjern EPS fradrag opp til beløpsgrense`(
                    periode = it.key,
                    beløpsgrense = periodisertSumGarantipensjonsnivå(it.key),
                    fradrag = it.value
                )
            }
        }
    }

    object EpsUnder67ÅrOgUførFlyktning : FradragStrategy(FradragStrategyName.EpsUnder67ÅrOgUførFlyktning) {
        override fun beregnFradrag(fradrag: Map<Periode, List<Fradrag>>): Map<Periode, List<Fradrag>> {
            return fradrag
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                .`fjern EPS fradrag opp til satsbeløp`()
        }

        override fun getEpsFribeløp(periode: Periode): Double = periodisertSumSatsbeløp(periode)

        private fun periodisertSumSatsbeløp(periode: Periode) =
            Sats.ORDINÆR.periodiser(periode).values.sumByDouble { it }

        private fun Map<Periode, List<Fradrag>>.`fjern EPS fradrag opp til satsbeløp`(): Map<Periode, List<Fradrag>> {
            return mapValues {
                `fjern EPS fradrag opp til beløpsgrense`(
                    it.key,
                    periodisertSumSatsbeløp(it.key),
                    it.value
                )
            }
        }
    }

    object EpsUnder67År : FradragStrategy(FradragStrategyName.EpsUnder67År) {
        override fun beregnFradrag(fradrag: Map<Periode, List<Fradrag>>): Map<Periode, List<Fradrag>> =
            fradrag
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                .`slå sammen eps sine fradrag til en og samme type`()

        override fun getEpsFribeløp(periode: Periode): Double = 0.0

        private fun Map<Periode, List<Fradrag>>.`slå sammen eps sine fradrag til en og samme type`(): Map<Periode, List<Fradrag>> {
            return mapValues { `slå sammen eps sine fradrag til en og samme type`(it.key, it.value) }
        }

        private fun `slå sammen eps sine fradrag til en og samme type`(
            periode: Periode,
            fradrag: List<Fradrag>
        ): List<Fradrag> {
            val (epsFradrag, søkersFradrag) = fradrag.partition { it.getTilhører() == FradragTilhører.EPS }
            if (epsFradrag.isEmpty()) return søkersFradrag
            val sammenslått = FradragFactory.periodiser(
                FradragFactory.ny(
                    type = Fradragstype.BeregnetFradragEPS,
                    månedsbeløp = epsFradrag.sumByDouble { it.getMånedsbeløp() },
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS
                )
            )
            return søkersFradrag.plus(sammenslått)
        }
    }

    protected fun Map<Periode, List<Fradrag>>.`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(): Map<Periode, List<Fradrag>> {
        return mapValues { `filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(it.value) }
    }

    private fun `filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(
        fradrag: List<Fradrag>
    ): List<Fradrag> {
        val arbeidsinntekter =
            fradrag.filter { it.getTilhører() == FradragTilhører.BRUKER && it.getFradragstype() == Fradragstype.Arbeidsinntekt }
        val forventetInntekt =
            fradrag.filter { it.getTilhører() == FradragTilhører.BRUKER && it.getFradragstype() == Fradragstype.ForventetInntekt }

        return if (arbeidsinntekter.sumByDouble { it.getMånedsbeløp() } > forventetInntekt.sumByDouble { it.getMånedsbeløp() })
            fradrag.minus(forventetInntekt)
        else
            fradrag.minus(arbeidsinntekter)
    }

    protected fun `fjern EPS fradrag opp til beløpsgrense`(
        periode: Periode,
        beløpsgrense: Double,
        fradrag: List<Fradrag>
    ): List<Fradrag> {
        val (epsFradrag, søkersFradrag) = fradrag.partition { it.getTilhører() == FradragTilhører.EPS }
        val epsFradragSum = epsFradrag.sumByDouble { it.getMånedsbeløp() }

        val beregnetFradragEps = epsFradragSum - beløpsgrense

        if (beregnetFradragEps <= 0) {
            return søkersFradrag
        }

        return søkersFradrag.plus(
            FradragFactory.periodiser(
                FradragFactory.ny(
                    type = Fradragstype.BeregnetFradragEPS,
                    månedsbeløp = beregnetFradragEps,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ),
        )
    }

    private fun List<Fradrag>.`har nøyaktig en forventet inntekt for bruker`() =
        singleOrNull { it.getTilhører() == FradragTilhører.BRUKER && it.getFradragstype() == Fradragstype.ForventetInntekt } != null

    companion object {
        fun fromName(name: FradragStrategyName) =
            when (name) {
                FradragStrategyName.Enslig -> Enslig
                FradragStrategyName.EpsOver67År -> EpsOver67År
                FradragStrategyName.EpsUnder67ÅrOgUførFlyktning -> EpsUnder67ÅrOgUførFlyktning
                FradragStrategyName.EpsUnder67År -> EpsUnder67År
            }
    }
}
