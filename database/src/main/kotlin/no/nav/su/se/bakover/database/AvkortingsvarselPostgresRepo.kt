package no.nav.su.se.bakover.database

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.oppdrag.Avkortingsvarsel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID
import javax.sql.DataSource

interface AvkortingsvarselRepo {
    fun hentUteståendeAvkortinger(sakId: UUID): List<Avkortingsvarsel.Utenlandsopphold.SkalAvkortes>
}

internal class AvkortingsvarselPostgresRepo(
    private val dataSource: DataSource,
) : AvkortingsvarselRepo {

    fun lagre(sakId: UUID, behandlingId: UUID, avkortingsvarsel: Avkortingsvarsel) {
        dataSource.withTransaction { tx ->
            slettForBehandling(behandlingId, tx)
            lagre(sakId, behandlingId, avkortingsvarsel, tx)
        }
    }

    enum class Status {
        OPPRETTET,
        SKAL_AVKORTES,
        AVKORTET
    }

    private fun lagre(sakId: UUID, behandlingId: UUID, avkortingsvarsel: Avkortingsvarsel, tx: TransactionalSession) {
        when (avkortingsvarsel) {
            Avkortingsvarsel.Ingen -> {}
            is Avkortingsvarsel.Utenlandsopphold.Avkortet -> {
                insert(
                    id = avkortingsvarsel.id,
                    opprettet = avkortingsvarsel.opprettet,
                    sakId = sakId,
                    behandlingId = behandlingId,
                    simulering = avkortingsvarsel.simulering,
                    feilutbetalingslinje = avkortingsvarsel.feilutbetalingslinje,
                    status = Status.AVKORTET,
                    tx = tx,
                )
            }
            is Avkortingsvarsel.Utenlandsopphold.Opprettet -> {
                insert(
                    id = avkortingsvarsel.id,
                    opprettet = avkortingsvarsel.opprettet,
                    sakId = sakId,
                    behandlingId = behandlingId,
                    simulering = avkortingsvarsel.simulering,
                    feilutbetalingslinje = avkortingsvarsel.feilutbetalingslinje,
                    status = Status.OPPRETTET,
                    tx = tx,
                )
            }
            is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> {
                insert(
                    id = avkortingsvarsel.id,
                    opprettet = avkortingsvarsel.opprettet,
                    sakId = sakId,
                    behandlingId = behandlingId,
                    simulering = avkortingsvarsel.simulering,
                    feilutbetalingslinje = avkortingsvarsel.feilutbetalingslinje,
                    status = Status.SKAL_AVKORTES,
                    tx = tx,
                )
            }
        }
    }

    private fun insert(
        id: UUID,
        opprettet: Tidspunkt,
        sakId: UUID,
        behandlingId: UUID,
        simulering: Simulering?,
        feilutbetalingslinje: Avkortingsvarsel.Utenlandsopphold.Feilutbetalingslinje?,
        status: Status,
        tx: TransactionalSession,
    ) {
        """insert into avkortingsvarsel (
            id, 
            opprettet, 
            sakId, 
            behandlingId,
            simulering, 
            feilutbetalingslinje,
            status
            ) values (
                :id, 
                :opprettet, 
                :sakId, 
                :behandlingId,
                to_jsonb(:simulering::json), 
                to_jsonb(:feilutbetalingslinje::json),
                :status
             )""".trimMargin()
            .insert(
                mapOf(
                    "id" to id,
                    "opprettet" to opprettet,
                    "sakId" to sakId,
                    "behandlingId" to behandlingId,
                    "simulering" to simulering?.let { objectMapper.writeValueAsString(it) },
                    "feilutbetalingslinje" to feilutbetalingslinje?.let { objectMapper.writeValueAsString(it) },
                    "status" to status.toString(),
                ),
                tx,
            )
    }

    override fun hentUteståendeAvkortinger(sakId: UUID): List<Avkortingsvarsel.Utenlandsopphold.SkalAvkortes> {
        return dataSource.withSession { session ->
            """select * from avkortingsvarsel where sakid = :sakid and status = :status""".hentListe(
                mapOf(
                    "sakid" to sakId,
                    "status" to "SKAL_AVKORTES",
                ),
                session,
            ) {
                it.toAvkortingsvarsel() as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes
            }
        }
    }

    fun slettForBehandling(behandlingId: UUID, tx: TransactionalSession) {
        """delete from avkortingsvarsel where behandlingId = :behandlingId""".oppdatering(
            mapOf(
                "behandlingId" to behandlingId,
            ),
            tx,
        )
    }

    fun hentForBehandling(behandlingId: UUID): Avkortingsvarsel {
        return dataSource.withSession { session ->
            """select * from avkortingsvarsel where behandlingId = :behandlingId""".hent(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                session,
            ) {
                it.toAvkortingsvarsel()
            }
        } ?: Avkortingsvarsel.Ingen
    }

    private fun Row.toAvkortingsvarsel(): Avkortingsvarsel.Utenlandsopphold {
        val opprettet = Avkortingsvarsel.Utenlandsopphold.Opprettet(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            simulering = string("simulering").let { objectMapper.readValue(it) },
            feilutbetalingslinje = string("feilutbetalingslinje").let { objectMapper.readValue(it) },
        )
        return when (Status.valueOf(string("status"))) {
            Status.OPPRETTET -> opprettet
            Status.SKAL_AVKORTES -> opprettet.skalAvkortes()
            Status.AVKORTET -> opprettet.skalAvkortes().avkortet()
        }
    }
}
