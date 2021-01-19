package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import javax.sql.DataSource

internal class UtbetalingPostgresRepo(
    private val dataSource: DataSource
) : UtbetalingRepo {
    override fun hentUtbetaling(utbetalingId: UUID30): Utbetaling.OversendtUtbetaling? =
        dataSource.withSession { session -> UtbetalingInternalRepo.hentUtbetalingInternal(utbetalingId, session) }

    override fun hentUtbetaling(avstemmingsnøkkel: Avstemmingsnøkkel): Utbetaling.OversendtUtbetaling? =
        dataSource.withSession { session ->
            "select u.*, s.saksnummer from utbetaling u left join sak s on s.id = u.sakId where u.avstemmingsnøkkel ->> 'nøkkel' = :nokkel".hent(
                mapOf(
                    "nokkel" to avstemmingsnøkkel.toString()
                ),
                session
            ) { it.toUtbetaling(session) }
        }

    override fun oppdaterMedKvittering(utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering) {
        dataSource.withSession { session ->
            "update utbetaling set kvittering = to_json(:kvittering::json) where id = :id".oppdatering(
                mapOf(
                    "id" to utbetaling.id,
                    "kvittering" to objectMapper.writeValueAsString(utbetaling.kvittering)
                ),
                session
            )
        }
    }

    override fun opprettUtbetaling(utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering) {
        dataSource.withSession { session ->
            """
            insert into utbetaling (id, opprettet, sakId, fnr, type, avstemmingsnøkkel, simulering, utbetalingsrequest, behandler)
            values (:id, :opprettet, :sakId, :fnr, :type, to_json(:avstemmingsnokkel::json), to_json(:simulering::json), to_json(:utbetalingsrequest::json), :behandler)
         """.oppdatering(
                mapOf(
                    "id" to utbetaling.id,
                    "opprettet" to utbetaling.opprettet,
                    "sakId" to utbetaling.sakId,
                    "fnr" to utbetaling.fnr,
                    "type" to utbetaling.type.name,
                    "avstemmingsnokkel" to objectMapper.writeValueAsString(utbetaling.avstemmingsnøkkel),
                    "simulering" to objectMapper.writeValueAsString(utbetaling.simulering),
                    "utbetalingsrequest" to objectMapper.writeValueAsString(utbetaling.utbetalingsrequest),
                    "behandler" to utbetaling.behandler.navIdent
                ),
                session
            )
        }
        utbetaling.utbetalingslinjer.forEach { opprettUtbetalingslinje(utbetaling.id, it) }
    }

    internal fun opprettUtbetalingslinje(utbetalingId: UUID30, utbetalingslinje: Utbetalingslinje): Utbetalingslinje {
        dataSource.withSession { session ->
            """
            insert into utbetalingslinje (id, opprettet, fom, tom, utbetalingId, forrigeUtbetalingslinjeId, beløp)
            values (:id, :opprettet, :fom, :tom, :utbetalingId, :forrigeUtbetalingslinjeId, :belop)
        """.oppdatering(
                mapOf(
                    "id" to utbetalingslinje.id,
                    "opprettet" to utbetalingslinje.opprettet,
                    "fom" to utbetalingslinje.fraOgMed,
                    "tom" to utbetalingslinje.tilOgMed,
                    "utbetalingId" to utbetalingId,
                    "forrigeUtbetalingslinjeId" to utbetalingslinje.forrigeUtbetalingslinjeId,
                    "belop" to utbetalingslinje.beløp,
                ),
                session
            )
        }
        return utbetalingslinje
    }
}
