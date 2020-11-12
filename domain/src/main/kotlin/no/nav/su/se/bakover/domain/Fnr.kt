package no.nav.su.se.bakover.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import java.time.LocalDate
import java.time.Period

// https://www.skatteetaten.no/person/folkeregister/fodsel-og-navnevalg/barn-fodt-i-norge/fodselsnummer/#:~:text=De%20seks%20f%C3%B8rste%20sifrene%20viser,fem%20siste%20sifrene%20i%20f%C3%B8dselsnummeret.

data class Fnr @JsonCreator(mode = DELEGATING) constructor(
    @JsonValue
    val fnr: String
) {
    private val fnrPattern = Regex("[0-9]{11}")

    init {
        validate(fnr)
    }

    fun getFødselsdag() = fnr.substring(0, 2)

    fun getFødselsmåned() = fnr.substring(2, 4)

    fun getFødselsår() = fnr.substring(4, 6)

    fun getIndividnummer() = fnr.substring(6, 9)

    fun getÅrhundre(): String {
        val individnummer = Integer.parseInt(getIndividnummer())
        val fødselsår = Integer.parseInt(getFødselsår())
        return when {
            individnummer in 0..499 && fødselsår in 0..99 -> "19"
            individnummer in 500..749 && fødselsår in 54..99 -> "18"
            individnummer in 500..999 && fødselsår in 0..39 -> "20"
            individnummer in 900..999 && fødselsår in 40..99 -> "19"
            else -> throw RuntimeException("Feil individnummer")
        }
    }

    fun getAlder() = Period.between(
        LocalDate.of(
            (getÅrhundre() + getFødselsår()).toInt(),
            getFødselsmåned().toInt(),
            getFødselsdag().toInt()
        ),
        LocalDate.now()
    ).years

    override fun toString(): String = fnr

    private fun validate(fnr: String) {
        if (!fnr.matches(fnrPattern)) throw UgyldigFnrException(fnr)
    }
}

class UgyldigFnrException(fnr: String?) : RuntimeException("Ugyldig fnr: $fnr")
