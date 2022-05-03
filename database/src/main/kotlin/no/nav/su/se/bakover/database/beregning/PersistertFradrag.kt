package no.nav.su.se.bakover.database.beregning

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragForMåned
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragForPeriode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt

/**
 * Vi bruker samme representasjon i databasen for et fradrag for en spesifikk måned eller for en lengre periode.
 */
internal data class PersistertFradrag(
    @JsonProperty("fradragstype")
    val kategori: Fradragstype.Kategori,
    val beskrivelse: String?,
    val månedsbeløp: Double,
    val utenlandskInntekt: UtenlandskInntekt?,
    val periode: PeriodeJson,
    val tilhører: FradragTilhører,
) {
    fun toFradragForMåned(): FradragForMåned {
        return FradragForMåned(
            fradragstype = Fradragstype.from(kategori, beskrivelse),
            månedsbeløp = månedsbeløp,
            måned = periode.toMånedsperiode(),
            utenlandskInntekt = utenlandskInntekt,
            tilhører = tilhører,
        )
    }

    fun toFradragForPeriode(): FradragForPeriode {
        return FradragForPeriode(
            fradragstype = Fradragstype.from(kategori, beskrivelse),
            månedsbeløp = månedsbeløp,
            periode = periode.toPeriode(),
            utenlandskInntekt = utenlandskInntekt,
            tilhører = tilhører,
        )
    }
}

/**
 * Mapper et [Fradrag] til en databaserepresentasjon.
 * Serialiseres/derserialiseres ikke direkte.
 */
internal fun Fradrag.toJson(): PersistertFradrag {
    return PersistertFradrag(
        kategori = fradragstype.kategori,
        beskrivelse = (fradragstype as? Fradragstype.Annet)?.beskrivelse,
        månedsbeløp = månedsbeløp,
        utenlandskInntekt = utenlandskInntekt,
        periode = periode.toJson(),
        tilhører = tilhører,
    )
}