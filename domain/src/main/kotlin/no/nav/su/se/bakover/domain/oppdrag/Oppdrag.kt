package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import java.time.Instant
import java.util.UUID

data class Oppdrag(
    override val id: UUID = UUID.randomUUID(), // oppdragsid/kankskje avstemmingsnøkkel?
    override val opprettet: Instant = now(),
    private val sakId: UUID, // fagsystemId,
    private val behandlingId: UUID,
    private val endringskode: Endringskode,
    private var simulering: Simulering? = null,
    private val oppdragslinjer: List<Oppdragslinje>
) : PersistentDomainObject<OppdragPersistenceObserver>(), DtoConvertable<OppdragDto> {
    enum class Endringskode {
        NY, ENDR
    }

    override fun toDto(): OppdragDto {
        return OppdragDto(id, opprettet, sakId, behandlingId, endringskode, oppdragslinjer)
    }

    fun addSimulering(simulering: Simulering) {
        this.simulering = persistenceObserver.addSimulering(id, simulering)
    }

    fun sisteOppdragslinje() = oppdragslinjer.last()
}

interface OppdragPersistenceObserver : PersistenceObserver {
    fun addSimulering(oppdragsId: UUID, simulering: Simulering): Simulering
}

data class OppdragDto(
    val id: UUID,
    val opprettet: Instant,
    val sakId: UUID,
    val behandlingId: UUID,
    val endringskode: Oppdrag.Endringskode,
    val oppdragslinjer: List<Oppdragslinje>
)
