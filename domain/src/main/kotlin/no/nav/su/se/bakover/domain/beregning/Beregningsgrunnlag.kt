package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import arrow.core.extensions.list.foldable.forAll
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag

internal data class Beregningsgrunnlag private constructor(
    val beregningsperiode: Periode,
    val fradrag: List<Fradrag>,
) {
    companion object {
        fun create(
            beregningsperiode: Periode,
            uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
            fradragFraSaksbehandler: List<Fradrag>,
        ): Beregningsgrunnlag {
            return tryCreate(
                beregningsperiode,
                uføregrunnlag,
                fradragFraSaksbehandler,
            ).getOrHandle { throw IllegalArgumentException(it.toString()) }
        }

        private fun tryCreate(
            beregningsperiode: Periode,
            uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
            fradragFraSaksbehandler: List<Fradrag>,
        ): Either<UgyldigBeregningsgrunnlag, Beregningsgrunnlag> {
            val fradrag: List<Fradrag> = fradragFraSaksbehandler.plus(
                uføregrunnlag.map {
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = it.forventetInntekt / 12.0,
                        periode = it.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    )
                }.ifEmpty {
                    // TODO jah: Dette er egentlig bare en snarvei for testene som går ut over typesikkerheten.
                    //  Vurder å bytt fra List<Grunnlag.Uføregrunnlag> til NonEmptyList<Grunnlag.Uføregrunnlag>
                    listOf(
                        FradragFactory.ny(
                            type = Fradragstype.ForventetInntekt,
                            månedsbeløp = 0.0,
                            periode = beregningsperiode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    )
                },
            )
            if (!fradrag.all { beregningsperiode inneholder it.periode }) {
                return UgyldigBeregningsgrunnlag.IkkeLovMedFradragUtenforPerioden.left()
            }

            fradrag.filter { it.getFradragstype() == Fradragstype.ForventetInntekt && it.getTilhører() == FradragTilhører.BRUKER }.let { forventedeInntekter ->
                if (forventedeInntekter.count() < 1) {
                    return UgyldigBeregningsgrunnlag.BrukerMåHaMinst1ForventetInntekt.left()
                }
                if (forventedeInntekter.forAll { f1 ->
                    forventedeInntekter.minus(f1).any { f2 -> f1.periode overlapper f2.periode }
                }
                ) {
                    return UgyldigBeregningsgrunnlag.OverlappendePerioderMedForventetInntekt.left()
                }
            }

            if (!beregningsperiode.tilMånedsperioder().forAll { it ->
                fradrag.flatMap { it.periode.tilMånedsperioder() }.contains(it)
            }
            ) {
                return UgyldigBeregningsgrunnlag.ManglerForventetInntektForEnkelteMåneder.left()
            }

            return Beregningsgrunnlag(beregningsperiode, fradrag).right()
        }
    }

    sealed class UgyldigBeregningsgrunnlag {
        object IkkeLovMedFradragUtenforPerioden : UgyldigBeregningsgrunnlag()
        object BrukerMåHaMinst1ForventetInntekt : UgyldigBeregningsgrunnlag()
        object OverlappendePerioderMedForventetInntekt : UgyldigBeregningsgrunnlag()
        object ManglerForventetInntektForEnkelteMåneder : UgyldigBeregningsgrunnlag()
    }
}
