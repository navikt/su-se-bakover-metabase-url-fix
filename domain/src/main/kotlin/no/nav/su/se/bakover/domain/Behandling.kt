package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.now

import no.nav.su.se.bakover.domain.VilkårsvurderingDto.Companion.toDto
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningDto
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling.Opprettet
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Behandling constructor(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Instant = now(),
    private val vilkårsvurderinger: MutableList<Vilkårsvurdering> = mutableListOf(),
    private val søknad: Søknad,
    private val beregninger: MutableList<Beregning> = mutableListOf(),
    private val utbetalinger: MutableList<Utbetaling> = mutableListOf(),
    private var status: BehandlingsStatus = BehandlingsStatus.OPPRETTET
) : PersistentDomainObject<BehandlingPersistenceObserver>(), DtoConvertable<BehandlingDto> {
    private var tilstand: Tilstand = resolve(status)

    fun status() = tilstand.status

    override fun toDto() = BehandlingDto(
        id = id,
        opprettet = opprettet,
        vilkårsvurderinger = vilkårsvurderinger.toDto(),
        søknad = søknad.toDto(),
        beregning = if (beregninger.isEmpty()) null else gjeldendeBeregning().toDto(),
        status = status,
        utbetaling = gjeldendeUtbetaling()
    )

    fun gjeldendeUtbetaling() = utbetalinger.sortedWith(Opprettet).lastOrNull()

    private fun resolve(status: BehandlingsStatus): Tilstand = when (status) {
        BehandlingsStatus.OPPRETTET -> Opprettet()
        BehandlingsStatus.VILKÅRSVURDERT -> Vilkårsvurdert()
        BehandlingsStatus.BEREGNET -> Beregnet()
        BehandlingsStatus.SIMULERT -> Simulert()
        BehandlingsStatus.INNVILGET -> Innvilget()
        BehandlingsStatus.AVSLÅTT -> Avslått()
        BehandlingsStatus.TIL_ATTESTERING -> TilAttestering()
    }

    fun opprettVilkårsvurderinger(): Behandling {
        tilstand.opprettVilkårsvurderinger()
        return this
    }

    fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>): Behandling {
        tilstand.oppdaterVilkårsvurderinger(oppdatertListe)
        return this
    }

    fun leggTilUtbetaling(utbetaling: Utbetaling) {
        tilstand.leggTilUtbetaling(utbetaling)
    }

    fun opprettBeregning(
        fom: LocalDate,
        tom: LocalDate,
        sats: Sats = Sats.HØY,
        fradrag: List<Fradrag> = emptyList()
    ): Behandling {
        tilstand.opprettBeregning(fom, tom, sats, fradrag)
        return this
    }

    // TODO stuff
    fun sendTilAttestering(): Behandling {
        tilstand.sendTilAttestering()
        return this
    }

    internal fun gjeldendeBeregning(): Beregning = beregninger.toList()
        .sortedWith(Beregning.Opprettet)
        .last()

    override fun equals(other: Any?) = other is Behandling && id == other.id
    override fun hashCode() = id.hashCode()

    private fun List<Vilkårsvurdering>.alleVurdert() = none { !it.vurdert() }
    private fun List<Vilkårsvurdering>.harAvslag() = any { it.avslått() }
    private fun List<Vilkårsvurdering>.innvilget() = alleVurdert() && !harAvslag()

    interface Tilstand {
        val status: BehandlingsStatus
        fun opprettVilkårsvurderinger() {
            throw TilstandException(status, this::opprettVilkårsvurderinger.toString())
        }

        fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>) {
            throw TilstandException(status, this::oppdaterVilkårsvurderinger.toString())
        }

        fun leggTilUtbetaling(utbetaling: Utbetaling) {
            throw TilstandException(status, this::leggTilUtbetaling.toString())
        }

        fun opprettBeregning(
            fom: LocalDate,
            tom: LocalDate,
            sats: Sats = Sats.HØY,
            fradrag: List<Fradrag>
        ) {
            throw TilstandException(status, this::opprettBeregning.toString())
        }

        fun sendTilAttestering() {
            throw TilstandException(status, this::sendTilAttestering.toString())
        }
    }

    private fun nyTilstand(target: Tilstand): Tilstand {
        status = persistenceObserver.oppdaterBehandlingStatus(id, target.status)
        tilstand = resolve(status)
        return tilstand
    }

    private inner class Opprettet : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.OPPRETTET
        override fun opprettVilkårsvurderinger() {
            if (vilkårsvurderinger.isNotEmpty()) throw TilstandException(
                status,
                this::opprettVilkårsvurderinger.toString()
            )
            vilkårsvurderinger.addAll(
                persistenceObserver.opprettVilkårsvurderinger(
                    behandlingId = id,
                    vilkårsvurderinger = Vilkår.values().map { Vilkårsvurdering(vilkår = it) }
                )
            )
        }

        override fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>) {
            oppdatertListe.forEach { oppdatert ->
                vilkårsvurderinger
                    .single { it == oppdatert }
                    .apply { oppdater(oppdatert) }
            }
            if (vilkårsvurderinger.innvilget()) {
                nyTilstand(Vilkårsvurdert())
            } else {
                if (vilkårsvurderinger.harAvslag()) {
                    nyTilstand(Avslått())
                }
            }
        }
    }

    private inner class Vilkårsvurdert : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT
        override fun opprettBeregning(fom: LocalDate, tom: LocalDate, sats: Sats, fradrag: List<Fradrag>) {
            beregninger.add(
                persistenceObserver.opprettBeregning(
                    behandlingId = id,
                    beregning = Beregning(
                        fom = fom,
                        tom = tom,
                        sats = sats,
                        fradrag = fradrag
                    )
                )
            )
            nyTilstand(Beregnet())
        }

        override fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>) {
            nyTilstand(Opprettet()).oppdaterVilkårsvurderinger(oppdatertListe)
        }
    }

    private inner class Beregnet : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET

        override fun leggTilUtbetaling(utbetaling: Utbetaling) {
            this@Behandling.utbetalinger.add(utbetaling)
            nyTilstand(Simulert())
        }

        override fun opprettBeregning(fom: LocalDate, tom: LocalDate, sats: Sats, fradrag: List<Fradrag>) {
            nyTilstand(Vilkårsvurdert()).opprettBeregning(fom, tom, sats, fradrag)
        }

        override fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>) {
            nyTilstand(Opprettet()).oppdaterVilkårsvurderinger(oppdatertListe)
        }
    }

    private inner class Simulert : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.SIMULERT

        override fun sendTilAttestering() {
            nyTilstand(TilAttestering())
        }

        override fun opprettBeregning(fom: LocalDate, tom: LocalDate, sats: Sats, fradrag: List<Fradrag>) {
            nyTilstand(Vilkårsvurdert()).opprettBeregning(fom, tom, sats, fradrag)
        }

        override fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>) {
            nyTilstand(Opprettet()).oppdaterVilkårsvurderinger(oppdatertListe)
        }
    }

    private inner class TilAttestering : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING
    }

    private inner class Avslått : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.AVSLÅTT
        override fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>) {
            nyTilstand(Opprettet()).oppdaterVilkårsvurderinger(oppdatertListe)
        }
    }

    private inner class Innvilget : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.INNVILGET
    }

    enum class BehandlingsStatus {
        OPPRETTET,
        VILKÅRSVURDERT,
        BEREGNET,
        SIMULERT,

        /*VEDTAKSBREV,*/
        INNVILGET,
        AVSLÅTT,
        TIL_ATTESTERING
    }

    class TilstandException(
        val state: BehandlingsStatus,
        val operation: String,
        val msg: String = "Illegal operation: $operation for state: $state"
    ) :
        RuntimeException(msg)
}

interface BehandlingPersistenceObserver : PersistenceObserver {
    fun opprettVilkårsvurderinger(
        behandlingId: UUID,
        vilkårsvurderinger: List<Vilkårsvurdering>
    ): List<Vilkårsvurdering>

    fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning
    fun oppdaterBehandlingStatus(
        behandlingId: UUID,
        status: Behandling.BehandlingsStatus
    ): Behandling.BehandlingsStatus
}

data class BehandlingDto(
    val id: UUID,
    val opprettet: Instant,
    val vilkårsvurderinger: List<VilkårsvurderingDto>,
    val søknad: SøknadDto,
    val beregning: BeregningDto?,
    val status: Behandling.BehandlingsStatus,
    val utbetaling: Utbetaling?
)
