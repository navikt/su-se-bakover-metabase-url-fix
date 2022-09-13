package no.nav.su.se.bakover.domain.brev.søknad.lukk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Person.Navn
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.brev.BrevInnhold.Personalia
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.test.fixedLocalDate
import org.junit.jupiter.api.Test

internal class AvvistSøknadBrevRequestTest {

    private val expectedPersonalia = Personalia(
        dato = fixedLocalDate.ddMMyyyy(),
        fødselsnummer = Fnr(fnr = "12345678901"),
        fornavn = "Tore",
        etternavn = "Strømøy",
        saksnummer = 2021,
    )

    private val person = Person(
        ident = Ident(
            fnr = Fnr(fnr = "12345678901"),
            aktørId = AktørId(aktørId = "123"),
        ),
        navn = Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
    )

    @Test
    fun `lager vedtaks-brevdata`() {
        AvvistSøknadBrevRequest(
            person = person,
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.VedtaksbrevUtenFritekst(),
            saksbehandlerNavn = "saksbehandler",
            dagensDato = fixedLocalDate,
            saksnummer = Saksnummer(2021),
        ).brevInnhold shouldBe AvvistSøknadVedtakBrevInnhold(
            expectedPersonalia,
            "saksbehandler",
            null,
        )
    }

    @Test
    fun `lager fritekst-brevdata`() {
        AvvistSøknadBrevRequest(
            person = person,
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("jeg er fritekst"),
            saksbehandlerNavn = "saksbehandler",
            dagensDato = fixedLocalDate,
            saksnummer = Saksnummer(2021),
        ).brevInnhold shouldBe AvvistSøknadFritekstBrevInnhold(
            personalia = expectedPersonalia,
            saksbehandlerNavn = "saksbehandler",
            fritekst = "jeg er fritekst",
        )
    }
}
