package no.nav.su.se.bakover.domain.vilkår

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

/**
Et inngangsvilkår er de vilkårene som kan føre til avslag før det beregnes?

Her har vi utelatt for høy inntekt (SU<0) og su under minstegrense (SU<2%)
 */
sealed class Inngangsvilkår {
    object Uførhet : Inngangsvilkår()
    object Flyktning : Inngangsvilkår()
    object Oppholdstillatelse : Inngangsvilkår()
    object PersonligOppmøte : Inngangsvilkår()
    object Formue : Inngangsvilkår()
    object BorOgOppholderSegINorge : Inngangsvilkår()
    object utenlandsoppholdOver90Dager : Inngangsvilkår()
    object innlagtPåInstitusjon : Inngangsvilkår()
}

data class Paragraf(
    val value: String,
    val lovverkVersjon: String,
)

data class Vilkårsvurderinger(
    val uføre: Vilkår<Grunnlag.Uføregrunnlag> = Vilkår.IkkeVurdert.Uførhet,
) {
    companion object {
        val EMPTY = Vilkårsvurderinger()
    }

    // data class IkkeVurdert(
    //     val uføre: Vilkårsvurdering<Grunnlag.Uføregrunnlag>? = null,
    // ): Vilkårsvurderinger()

    // private val alleVilkår: Set<Vilkårsvurdering<*>> = setOf(uføre)

    // fun hentResultat(): Vilkårsvurderingsresultat {
    //     return when {
    //         erInnvilget() -> Vilkårsvurderingsresultat.Innvilget(alleVilkår)
    //         erAvslag() -> Vilkårsvurderingsresultat.Avslag(
    //             value.filter { it.vurdering.resultat is Resultat.Avslag }
    //                 .toSet(),
    //         )
    //         else -> Vilkårsvurderingsresultat.Uavklart(emptySet()) // TODO fyll inn
    //     }
    // }
    //
    // private fun erInnvilget(): Boolean {
    //     return alleVilkår.all { it.resultat is Resultat.Innvilget }
    // }
    //
    // private fun erAvslag(): Boolean {
    //     return value.any { it.vurdering.resultat is Resultat.Avslag }
    // }
}

/**
 * vilkårsvurderinger - inneholder vilkårsvurdering av alle grunnlagstyper
 * vilkårsvurdering - aggregert vilkårsvurdering for en enkelt type grunnlag inneholder flere vurderingsperioder (en periode per grunnlag)
 * vurderingsperiode - inneholder vilkårsvurdering for ett enkelt grunnlag (kan være manuell (kan vurderes uten grunnlag) eller automatisk (har alltid grunnlag))
 * grunnlag - informasjon for en spesifikk periode som forteller noe om oppfyllelsen av et vilkår
 */

sealed class Vilkårsvurderingsresultat {
    data class Avslag(
        val vilkår: Set<Vilkår<*>>,
    ) : Vilkårsvurderingsresultat()

    data class Innvilget(
        val vilkår: Set<Vilkår<*>>,
    ) : Vilkårsvurderingsresultat()

    data class Uavklart(
        val vilkår: Set<Inngangsvilkår>,
    ) : Vilkårsvurderingsresultat()
}

/**
 * Vurderingen av et vilkår mot en eller flere grunnlagsdata
 */
sealed class Vilkår<T : Grunnlag> {

    sealed class IkkeVurdert<T : Grunnlag> : Vilkår<T>() {
        object Uførhet : Vilkår<Grunnlag.Uføregrunnlag>()
    }

    sealed class Vurdert<T : Grunnlag> : Vilkår<T>() {
        abstract val vilkår: Inngangsvilkår
        abstract val grunnlag: List<T>
        abstract val vurdering: List<Vurderingsperiode<T>>

        val resultat: Resultat by lazy {
            if (erInnvilget) Resultat.Innvilget else if (erAvslag) Resultat.Avslag else Resultat.Uavklart
        }

        val erInnvilget: Boolean by lazy {
            vurdering.all { it.resultat == Resultat.Innvilget }
        }

        val erAvslag: Boolean by lazy {
            vurdering.any { it.resultat == Resultat.Avslag }
        }

        data class Uførhet(
            override val vurdering: List<Vurderingsperiode<Grunnlag.Uføregrunnlag>>,
        ) : Vurdert<Grunnlag.Uføregrunnlag>() {
            override val vilkår = Inngangsvilkår.Uførhet
            override val grunnlag: List<Grunnlag.Uføregrunnlag> = vurdering.mapNotNull {
                it.grunnlag
            }

            companion object {
                fun manuell(
                    resultat: Resultat,
                    begrunnelse: String,
                    grunnlag: List<Grunnlag.Uføregrunnlag>,
                    periode: Periode,
                ) = Uførhet(
                    vurdering = grunnlag.map {
                        Vurderingsperiode.Manuell(
                            resultat = resultat,
                            grunnlag = it,
                            begrunnelse = begrunnelse,
                            periode = it.periode,
                        )
                    }.ifEmpty {
                        listOf(
                            Vurderingsperiode.Manuell<Grunnlag.Uføregrunnlag>(
                                resultat = resultat,
                                grunnlag = null,
                                begrunnelse = begrunnelse,
                                periode = periode,
                            ),
                        )
                    },
                )
            }
        }

//    data class Flyktning(
//        override val id: UUID = UUID.randomUUID(),
//        override val opprettet: Tidspunkt = Tidspunkt.now(),
//        override val vurdering: Vurdering,
//        override val grunnlag: Grunnlag.Flyktninggrunnlag,
//    ) : Vilkårsvurdering<Grunnlag.Flyktninggrunnlag>() {
//        override val vilkår = Inngangsvilkår.Flyktning
//    }
    }
}

sealed class Vurderingsperiode<T : Grunnlag> {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val resultat: Resultat
    abstract val grunnlag: T?
    abstract val periode: Periode
    abstract val begrunnelse: String?

    data class Manuell<T : Grunnlag>(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val resultat: Resultat,
        override val grunnlag: T?,
        override val periode: Periode,
        override val begrunnelse: String?,
    ) : Vurderingsperiode<T>()
}

sealed class Resultat {
    object Avslag : Resultat()
    object Innvilget : Resultat()
    object Uavklart : Resultat()
}
