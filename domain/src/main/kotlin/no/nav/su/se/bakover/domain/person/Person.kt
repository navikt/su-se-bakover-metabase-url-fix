package no.nav.su.se.bakover.domain.person

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Ident
import java.time.LocalDate
import java.time.Period
import java.time.Year

data class Person(
    val ident: Ident,
    val navn: Navn,
    val telefonnummer: Telefonnummer? = null,
    val adresse: List<Adresse>? = null,
    val statsborgerskap: String? = null,
    val sivilstand: Sivilstand? = null,
    val kjønn: String? = null,
    val fødsel: Fødsel? = null,
    val adressebeskyttelse: String? = null,
    val skjermet: Boolean? = null,
    val kontaktinfo: Kontaktinfo? = null,
    val vergemål: Boolean? = null,
    val fullmakt: Boolean? = null,
    val dødsdato: LocalDate? = null,
) {
    fun getAlder(påDato: LocalDate): Int? = fødsel?.dato?.let { Period.between(it, påDato).years }
    fun er67EllerEldre(påDato: LocalDate): Boolean? = getAlder(påDato)?.let { it >= 67 }

    data class Navn(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
    )

    data class Adresse(
        val adresselinje: String?,
        val poststed: Poststed?,
        val bruksenhet: String?,
        val kommune: Kommune?,
        val landkode: String? = null,
        val adressetype: String,
        val adresseformat: String,
    )

    data class Kommune(
        val kommunenummer: String,
        val kommunenavn: String?,
    )

    data class Poststed(
        val postnummer: String,
        val poststed: String?,
    )

    data class Kontaktinfo(
        val epostadresse: String?,
        val mobiltelefonnummer: String?,
        val språk: String?,
        val kanKontaktesDigitalt: Boolean,
    )

    data class Sivilstand(
        val type: SivilstandTyper,
        val relatertVedSivilstand: Fnr?,
    )

    data class Fødsel(
        val dato: LocalDate? = null,
        val år: Year,
    ) {
        init {
            if (dato != null) {
                require(dato.year == år.value) { "Året på fødselsdatoen og fødselsåret som er angitt er ikke lik. fødelsdato ${dato.year}, fødselsår ${år.value}" }
            }
        }
    }
}
