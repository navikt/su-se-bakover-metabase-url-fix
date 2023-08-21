package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * @property hendelseId unikt identifiserer denne hendelsen.
 * @property entitetId Også kalt streamId. knytter et domeneområdet sammen (f.eks sak)
 * @property versjon rekkefølgen hendelser skjer innenfor [entitetId]
 * @property hendelsestidspunkt Tidspunktet hendelsen skjedde fra domenet sin side.
 * @property triggetAv hvilken hendelse førte til at denne hendelsen ble opprettet - merk at denne er kun for interne hendelser
 * @property meta metadata knyttet til hendelsen for auditing/tracing/debug-formål.
 */
interface Hendelse<T : Hendelse<T>> : Comparable<T> {
    val hendelseId: HendelseId
    val tidligereHendelseId: HendelseId?
    val entitetId: UUID
    val versjon: Hendelsesversjon
    val hendelsestidspunkt: Tidspunkt
    val triggetAv: HendelseId?
    val meta: HendelseMetadata
}
