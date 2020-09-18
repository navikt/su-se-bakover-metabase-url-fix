package no.nav.su.se.bakover.web.routes.hendelse

import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.Hendelse
import java.time.Instant

internal fun Hendelse.toJson() = HendelseJson(
    overskrift = overskrift,
    underoverskrift = underoverskrift,
    tidspunkt = tidspunkt,
    melding = melding
)

internal data class HendelseJson(
    val overskrift: String,
    val underoverskrift: String,
    val tidspunkt: Instant,
    val melding: String
)

internal fun List<Hendelse>.toJson() = this.map {
    it.toJson()
}
