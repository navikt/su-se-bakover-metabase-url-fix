package vilkår.common.domain

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf

/**
 * https://confluence.adeo.no/pages/viewpage.action?pageId=414002852
 */
enum class Avslagsgrunn(val paragrafer: NonEmptyList<Int>) {
    UFØRHET(nonEmptyListOf(1, 2)),
    FLYKTNING(nonEmptyListOf(2, 3)),
    OPPHOLDSTILLATELSE(nonEmptyListOf(1, 2)),
    PERSONLIG_OPPMØTE(nonEmptyListOf(17)),
    FORMUE(nonEmptyListOf(8)),
    BOR_OG_OPPHOLDER_SEG_I_NORGE(nonEmptyListOf(1, 2, 3, 4)),
    FOR_HØY_INNTEKT(nonEmptyListOf(5, 6, 7)),
    SU_UNDER_MINSTEGRENSE(nonEmptyListOf(5, 6, 9)),
    UTENLANDSOPPHOLD_OVER_90_DAGER(nonEmptyListOf(1, 2, 4)),
    INNLAGT_PÅ_INSTITUSJON(nonEmptyListOf(12)),
    MANGLENDE_DOKUMENTASJON(nonEmptyListOf(18)),
    SØKNAD_MANGLER_DOKUMENTASJON(nonEmptyListOf(18)),

    MANGLER_VEDTAK_ALDERSPENSJON_FOLKETRYGDEN(nonEmptyListOf(3)),
    MANGLER_VEDTAK_ANDRE_NORSKE_PENSJONSORDNINGER(nonEmptyListOf(3)),
    MANGLER_VEDTAK_UTENLANDSKE_PENSJONSORDNINGER(nonEmptyListOf(3)),
    FAMILIEGJENFORENING(nonEmptyListOf(3)),

//    // TODO: bør lage en paragraf-type/enum
//    fun getParagrafer() = when (this) {
//        UFØRHET -> listOf(1, 2)
//        FLYKTNING -> listOf(2, 3)
//        OPPHOLDSTILLATELSE -> listOf(1, 2)
//        PERSONLIG_OPPMØTE -> listOf(17)
//        FORMUE -> listOf(8)
//        BOR_OG_OPPHOLDER_SEG_I_NORGE -> listOf(1, 2, 3, 4)
//        FOR_HØY_INNTEKT -> listOf(5, 6, 7)
//        SU_UNDER_MINSTEGRENSE -> listOf(5, 6, 9)
//        UTENLANDSOPPHOLD_OVER_90_DAGER -> listOf(1, 2, 4)
//        INNLAGT_PÅ_INSTITUSJON -> listOf(12)
//        MANGLENDE_DOKUMENTASJON -> listOf(18)
//        SØKNAD_MANGLER_DOKUMENTASJON -> listOf(18)
//        MANGLER_VEDTAK_ALDERSPENSJON_FOLKETRYGDEN -> listOf(3)
//        MANGLER_VEDTAK_ANDRE_NORSKE_PENSJONSORDNINGER -> listOf(3)
//        MANGLER_VEDTAK_UTENLANDSKE_PENSJONSORDNINGER -> listOf(3)
//        FAMILIEGJENFORENING -> listOf(3)
//    }
}

fun List<Avslagsgrunn>.getDistinkteParagrafer(): List<Int> =
    this.map { it.paragrafer }.flatten().distinct().sorted()
