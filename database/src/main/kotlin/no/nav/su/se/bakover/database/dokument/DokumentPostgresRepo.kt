package no.nav.su.se.bakover.database.dokument

import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.persistence.Session
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.persistence.TransactionalSession
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.common.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.oppdatering
import no.nav.su.se.bakover.common.persistence.tidspunkt
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import java.time.Clock
import java.util.UUID

internal class DokumentPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val clock: Clock,
) : DokumentRepo {

    private val joinDokumentOgDistribusjonQuery =
        "select d.*, dd.journalpostid, dd.brevbestillingid from dokument d left join dokument_distribusjon dd on dd.dokumentid = d.id"

    override fun lagre(dokument: Dokument.MedMetadata, transactionContext: TransactionContext) {
        dbMetrics.timeQuery("lagreDokumentMedMetadata") {
            transactionContext.withTransaction { tx ->
                """
                insert into dokument(id, opprettet, sakId, generertDokument, generertDokumentJson, type, tittel, søknadId, vedtakId, revurderingId, klageId)
                values (:id, :opprettet, :sakId, :generertDokument, to_json(:generertDokumentJson::json), :type, :tittel, :soknadId, :vedtakId, :revurderingId, :klageId)
                """.trimIndent()
                    .insert(
                        mapOf(
                            "id" to dokument.id,
                            "opprettet" to dokument.opprettet,
                            "sakId" to dokument.metadata.sakId,
                            "generertDokument" to dokument.generertDokument,
                            // Dette er allerede gyldig json lagret som en String.
                            "generertDokumentJson" to dokument.generertDokumentJson,
                            "type" to when (dokument) {
                                is Dokument.MedMetadata.Informasjon.Viktig -> DokumentKategori.INFORMASJON_VIKTIG
                                is Dokument.MedMetadata.Informasjon.Annet -> DokumentKategori.INFORMASJON_ANNET
                                is Dokument.MedMetadata.Vedtak -> DokumentKategori.VEDTAK
                            }.toString(),
                            "tittel" to dokument.tittel,
                            "soknadId" to dokument.metadata.søknadId,
                            "vedtakId" to dokument.metadata.vedtakId,
                            "revurderingId" to dokument.metadata.revurderingId,
                            "klageId" to dokument.metadata.klageId,
                        ),
                        tx,
                    )

                lagreDokumentdistribusjon(
                    dokumentdistribusjon = Dokumentdistribusjon(
                        id = UUID.randomUUID(),
                        opprettet = dokument.opprettet,
                        endret = dokument.opprettet,
                        dokument = dokument,
                        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
                    ),
                    tx,
                )
            }
        }
    }

    override fun hentDokument(id: UUID): Dokument.MedMetadata? {
        return dbMetrics.timeQuery("hentDokumentMedMetadataForDokumentId") {
            sessionFactory.withSession { session ->
                hentDokument(id, session)
            }
        }
    }

    override fun hentForSak(id: UUID): List<Dokument.MedMetadata> {
        return dbMetrics.timeQuery("hentDokumentMedMetadataForSakId") {
            sessionFactory.withSession { session ->
                """
                $joinDokumentOgDistribusjonQuery where sakId = :id
                """.trimIndent()
                    .hentListe(mapOf("id" to id), session) {
                        it.toDokumentMedStatus()
                    }
            }
        }
    }

    override fun hentForSøknad(id: UUID): List<Dokument.MedMetadata> {
        return dbMetrics.timeQuery("hentDokumentMedMetadataForSøknadId") {
            sessionFactory.withSession { session ->
                """
                $joinDokumentOgDistribusjonQuery where søknadId = :id
                """.trimIndent()
                    .hentListe(mapOf("id" to id), session) {
                        it.toDokumentMedStatus()
                    }
            }
        }
    }

    override fun hentForVedtak(id: UUID): List<Dokument.MedMetadata> {
        return dbMetrics.timeQuery("hentDokumentMedMetadataForVedtakId") {
            sessionFactory.withSession { session ->
                """
                $joinDokumentOgDistribusjonQuery where vedtakId = :id
                """.trimIndent()
                    .hentListe(mapOf("id" to id), session) {
                        it.toDokumentMedStatus()
                    }
            }
        }
    }

    override fun hentForRevurdering(id: UUID): List<Dokument.MedMetadata> {
        return dbMetrics.timeQuery("hentDokumentMedMetadataForRevurderingId") {
            sessionFactory.withSession { session ->
                """
                $joinDokumentOgDistribusjonQuery where revurderingId = :id
                """.trimIndent()
                    .hentListe(mapOf("id" to id), session) {
                        it.toDokumentMedStatus()
                    }
            }
        }
    }

    override fun hentForKlage(id: UUID): List<Dokument.MedMetadata> {
        return dbMetrics.timeQuery("hentDokumentMedMetadataForKlageId") {
            sessionFactory.withSession { session ->
                """
                $joinDokumentOgDistribusjonQuery where klageId = :id
                """.trimIndent()
                    .hentListe(mapOf("id" to id), session) {
                        it.toDokumentMedStatus()
                    }
            }
        }
    }

    override fun hentDokumentdistribusjon(id: UUID): Dokumentdistribusjon? {
        return dbMetrics.timeQuery("hentDokumentdistribusjon") {
            sessionFactory.withSession { session ->
                """
                select * from dokument_distribusjon where id = :id
                """.trimIndent()
                    .hent(
                        mapOf(
                            "id" to id,
                        ),
                        session,
                    ) {
                        it.toDokumentdistribusjon(session)
                    }
            }
        }
    }

    override fun hentDokumenterForDistribusjon(): List<Dokumentdistribusjon> {
        return dbMetrics.timeQuery("hentDokumenterForDistribusjon") {
            sessionFactory.withSession { session ->
                """
                select * from dokument_distribusjon
                where journalpostId is null or brevbestillingId is null
                order by opprettet asc
                limit 10
                """.trimIndent()
                    .hentListe(emptyMap(), session) {
                        it.toDokumentdistribusjon(session)
                    }
            }
        }
    }

    override fun oppdaterDokumentdistribusjon(dokumentdistribusjon: Dokumentdistribusjon) {
        dbMetrics.timeQuery("oppdaterDokumentdistribusjon") {
            sessionFactory.withSession { session ->
                """
                update dokument_distribusjon set
                    journalpostId = :journalpostId,
                    brevbestillingId = :brevbestillingId,
                    endret = :endret
                where id = :id
                """.trimIndent()
                    .oppdatering(
                        mapOf(
                            "id" to dokumentdistribusjon.id,
                            "journalpostId" to JournalføringOgBrevdistribusjon.iverksattJournalpostId(
                                dokumentdistribusjon.journalføringOgBrevdistribusjon,
                            ),
                            "brevbestillingId" to JournalføringOgBrevdistribusjon.iverksattBrevbestillingId(
                                dokumentdistribusjon.journalføringOgBrevdistribusjon,
                            ),
                            "endret" to Tidspunkt.now(clock),
                        ),
                        session,
                    )
            }
        }
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }

    private fun lagreDokumentdistribusjon(dokumentdistribusjon: Dokumentdistribusjon, tx: TransactionalSession) {
        """
            insert into dokument_distribusjon(id, opprettet, endret, dokumentId, journalpostId, brevbestillingId)
            values (:id, :opprettet, :endret, :dokumentId, :journalpostId, :brevbestillingId)
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to dokumentdistribusjon.id,
                    "opprettet" to dokumentdistribusjon.opprettet,
                    "endret" to dokumentdistribusjon.endret,
                    "dokumentId" to dokumentdistribusjon.dokument.id,
                ),
                tx,
            )
    }

    private fun hentDokument(id: UUID, session: Session) =
        """
            $joinDokumentOgDistribusjonQuery where d.id = :id
        """.trimIndent()
            .hent(mapOf("id" to id), session) {
                it.toDokumentMedStatus()
            }

    private fun Row.toDokumentMedStatus(): Dokument.MedMetadata {
        val type = DokumentKategori.valueOf(string("type"))
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")
        val innhold = bytes("generertDokument")
        val request = string("generertDokumentJson")
        val sakId = uuid("sakid")
        val søknadId = uuidOrNull("søknadId")
        val vedtakId = uuidOrNull("vedtakId")
        val revurderingId = uuidOrNull("revurderingId")
        val klageId = uuidOrNull("klageId")
        val tittel = string("tittel")
        val brevbestillingId = stringOrNull("brevbestillingid")
        val journalpostId = stringOrNull("journalpostid")

        return when (type) {
            DokumentKategori.INFORMASJON_VIKTIG -> Dokument.MedMetadata.Informasjon.Viktig(
                id = id,
                opprettet = opprettet,
                tittel = tittel,
                generertDokument = innhold,
                generertDokumentJson = request,
                metadata = Dokument.Metadata(
                    sakId = sakId,
                    søknadId = søknadId,
                    vedtakId = vedtakId,
                    revurderingId = revurderingId,
                    klageId = klageId,
                    brevbestillingId = brevbestillingId,
                    journalpostId = journalpostId,
                ),
            )

            DokumentKategori.INFORMASJON_ANNET -> Dokument.MedMetadata.Informasjon.Annet(
                id = id,
                opprettet = opprettet,
                tittel = tittel,
                generertDokument = innhold,
                generertDokumentJson = request,
                metadata = Dokument.Metadata(
                    sakId = sakId,
                    søknadId = søknadId,
                    vedtakId = vedtakId,
                    revurderingId = revurderingId,
                    klageId = klageId,
                    brevbestillingId = brevbestillingId,
                    journalpostId = journalpostId,
                ),
            )

            DokumentKategori.VEDTAK -> Dokument.MedMetadata.Vedtak(
                id = id,
                opprettet = opprettet,
                tittel = tittel,
                generertDokument = innhold,
                generertDokumentJson = request,
                metadata = Dokument.Metadata(
                    sakId = sakId,
                    søknadId = søknadId,
                    vedtakId = vedtakId,
                    revurderingId = revurderingId,
                    klageId = klageId,
                    brevbestillingId = brevbestillingId,
                    journalpostId = journalpostId,
                ),
            )
        }
    }

    private enum class DokumentKategori {
        INFORMASJON_VIKTIG,
        INFORMASJON_ANNET,
        VEDTAK,
    }

    private fun Row.toDokumentdistribusjon(session: Session): Dokumentdistribusjon {
        return Dokumentdistribusjon(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            endret = tidspunkt("endret"),
            dokument = hentDokument(uuid("dokumentId"), session)!!,
            journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.fromId(
                iverksattJournalpostId = stringOrNull("journalpostid")?.let { JournalpostId(it) },
                iverksattBrevbestillingId = stringOrNull("brevbestillingid")?.let { BrevbestillingId(it) },
            ),
        )
    }
}
