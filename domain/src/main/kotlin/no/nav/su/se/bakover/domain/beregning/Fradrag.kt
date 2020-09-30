package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

enum class Fradragstype {
    Uføretrygd,
    Barnetillegg,
    Arbeidsinntekt,
    Pensjon,
    Kapitalinntekt,
    UtenlandskInntekt,
    ForventetInntekt,
    AndreYtelser;

    companion object {
        fun isValid(s: String) =
            runBlocking {
                Either.catch { valueOf(s) }
                    .isRight()
            }
    }
}

data class Fradrag(
    val id: UUID = UUID.randomUUID(),
    val type: Fradragstype,
    val beløp: Int,
    val fraUtlandInntekt: FraUtlandInntekt?,
    val delerAvPeriode: DelerAvPeriode?
) {
    fun perMåned(): Int = BigDecimal(beløp).divide(BigDecimal(12), 0, RoundingMode.HALF_UP).toInt()
}

data class FraUtlandInntekt(
    val beløpUtenlandskValuta: Int,
    val valuta: String,
    val kurs: Int
)

data class DelerAvPeriode(
    val fraOgMed: String,
    val tilOgMed: String
)
