package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import arrow.core.Either
import arrow.core.getOrHandle
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest.Oppdrag
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.KvitteringResponse.Alvorlighetsgrad.ALVORLIG_FEIL
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.KvitteringResponse.Alvorlighetsgrad.OK
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.KvitteringResponse.Alvorlighetsgrad.OK_MED_VARSEL
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.KvitteringResponse.Alvorlighetsgrad.SQL_FEIL
import java.time.Clock

/**
 * https://confluence.adeo.no/display/OKSY/Returdata+fra+Oppdragssystemet+til+fagrutinen
 */
@JacksonXmlRootElement(localName = "Oppdrag")
data class KvitteringResponse(
    val mmel: Mmel,
    @field:JacksonXmlProperty(localName = "oppdrag-110")
    val oppdrag: Oppdrag
) {
    data class Mmel(
        val systemId: String?,
        val kodeMelding: String?,
        val alvorlighetsgrad: Alvorlighetsgrad,
        val beskrMelding: String?,
        val sqlKode: String?,
        val sqlState: String?,
        val sqlMelding: String?,
        val mqCompletionKode: String?,
        val mqReasonKode: String?,
        val programId: String?,
        val sectionNavn: String?,
    )

    enum class Alvorlighetsgrad(@JsonValue val value: String) {
        OK("00"),
        /** En varselmelding følger med */
        OK_MED_VARSEL("04"),
        /** Alvorlig feil som logges og stopper behandling av aktuelt tilfelle*/
        ALVORLIG_FEIL("08"),
        SQL_FEIL("12");

        override fun toString() = value
    }

    fun toKvittering(originalKvittering: String, clock: Clock) = Kvittering(
        utbetalingsstatus = when (mmel.alvorlighetsgrad) {
            OK -> Utbetalingsstatus.OK
            OK_MED_VARSEL -> Utbetalingsstatus.OK_MED_VARSEL
            ALVORLIG_FEIL, SQL_FEIL -> Utbetalingsstatus.FEIL
        },
        originalKvittering = originalKvittering,
        mottattTidspunkt = now(clock)
    )

    companion object {
        internal fun String.toKvitteringResponse(xmlMapper: XmlMapper): KvitteringResponse = this
            .replace("<oppdrag xmlns", "<Oppdrag xmlns")
            .let {
                runBlocking {
                    Either.catch {
                        xmlMapper.readValue<KvitteringResponse>(it)
                    }.getOrHandle {
                        // TODO metric og sikkerlogg
                        throw it
                    }
                }
            }
    }
}
