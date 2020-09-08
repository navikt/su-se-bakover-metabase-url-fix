package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.KvitteringResponse.Companion.toKvitteringResponse
import org.junit.jupiter.api.Test

internal class KvitteringResponseTest {

    @Test
    fun `deserialiserer KvitteringRespons`() {

        kvitteringXml().toKvitteringResponse(KvitteringConsumer.xmlMapper) shouldBe KvitteringResponse(
            mmel = KvitteringResponse.Mmel(
                systemId = "231-OPPD",
                kodeMelding = null,
                alvorlighetsgrad = KvitteringResponse.Alvorlighetsgrad.ALVORLIG_FEIL,
                beskrMelding = null,
                sqlKode = null,
                sqlState = null,
                sqlMelding = null,
                mqCompletionKode = null,
                mqReasonKode = null,
                programId = null,
                sectionNavn = null
            ),
            oppdragRequest = UtbetalingRequest.OppdragRequest(
                kodeAksjon = UtbetalingRequest.KodeAksjon.UTBETALING,
                kodeEndring = UtbetalingRequest.KodeEndring.NY,
                kodeFagomraade = "SUUFORE",
                fagsystemId = "35413bd5-f66a-44d8-b7e9-1006d5",
                utbetFrekvens = UtbetalingRequest.Utbetalingsfrekvens.MND,
                oppdragGjelderId = "18127621833",
                datoOppdragGjelderFom = "1970-01-01",
                saksbehId = "SU",
                avstemming = UtbetalingRequest.Avstemming(
                    kodeKomponent = "SUUFORE",
                    nokkelAvstemming = "2a08e16a-7569-47cd-b600-039158",
                    tidspktMelding = "2020-09-02-15.57.08.298000"
                ),
                oppdragsEnheter = listOf(
                    UtbetalingRequest.OppdragsEnhet(
                        typeEnhet = "BOS",
                        enhet = "8020",
                        datoEnhetFom = "1970-01-01"
                    )
                ),
                oppdragslinjer = listOf(
                    UtbetalingRequest.Oppdragslinje(
                        kodeEndringLinje = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.NY,
                        delytelseId = "4fad33a7-9a7d-4732-9d3f-b9d0fc",
                        kodeKlassifik = "SUUFORE",
                        datoVedtakFom = "2020-01-01",
                        datoVedtakTom = "2020-12-31",
                        sats = "20637",
                        fradragTillegg = UtbetalingRequest.Oppdragslinje.FradragTillegg.TILLEGG,
                        typeSats = UtbetalingRequest.Oppdragslinje.TypeSats.MND,
                        brukKjoreplan = "N",
                        saksbehId = "SU",
                        utbetalesTilId = "18127621833",
                        refDelytelseId = null,
                        refFagsystemId = null
                    )
                )
            )
        )
    }

    companion object {
        //language=XML
        fun kvitteringXml(nokkelAvstemming: String = "2a08e16a-7569-47cd-b600-039158") =
            """
<?xml version="1.0" encoding="UTF-8"?>
<oppdrag xmlns="http://www.trygdeetaten.no/skjema/oppdrag">
   <mmel>
      <systemId>231-OPPD</systemId>
      <alvorlighetsgrad>08</alvorlighetsgrad>
   </mmel>
   <oppdrag-110>
      <kodeAksjon>1</kodeAksjon>
      <kodeEndring>NY</kodeEndring>
      <kodeFagomraade>SUUFORE</kodeFagomraade>
      <fagsystemId>35413bd5-f66a-44d8-b7e9-1006d5</fagsystemId>
      <utbetFrekvens>MND</utbetFrekvens>
      <oppdragGjelderId>18127621833</oppdragGjelderId>
      <datoOppdragGjelderFom>1970-01-01</datoOppdragGjelderFom>
      <saksbehId>SU</saksbehId>
      <avstemming-115>
         <kodeKomponent>SUUFORE</kodeKomponent>
         <nokkelAvstemming>$nokkelAvstemming</nokkelAvstemming>
         <tidspktMelding>2020-09-02-15.57.08.298000</tidspktMelding>
      </avstemming-115>
      <oppdrags-enhet-120>
         <typeEnhet>BOS</typeEnhet>
         <enhet>8020</enhet>
         <datoEnhetFom>1970-01-01</datoEnhetFom>
      </oppdrags-enhet-120>
      <oppdrags-linje-150>
         <kodeEndringLinje>NY</kodeEndringLinje>
         <delytelseId>4fad33a7-9a7d-4732-9d3f-b9d0fc</delytelseId>
         <kodeKlassifik>SUUFORE</kodeKlassifik>
         <datoVedtakFom>2020-01-01</datoVedtakFom>
         <datoVedtakTom>2020-12-31</datoVedtakTom>
         <sats>20637</sats>
         <fradragTillegg>T</fradragTillegg>
         <typeSats>MND</typeSats>
         <brukKjoreplan>N</brukKjoreplan>
         <saksbehId>SU</saksbehId>
         <utbetalesTilId>18127621833</utbetalesTilId>
         <ukjentFeltBørIgnorereres>ukjent</ukjentFeltBørIgnorereres>
      </oppdrags-linje-150>
   </oppdrag-110>
</Oppdrag>
            """.trimIndent()
    }
}
