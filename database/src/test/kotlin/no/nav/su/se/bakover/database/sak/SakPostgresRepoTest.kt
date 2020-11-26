package no.nav.su.se.bakover.database.sak

import com.nhaarman.mockitokotlin2.mock
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotliquery.queryOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Sak
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException

internal class SakPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = SakPostgresRepo(EmbeddedDatabase.instance(), mock())

    @Test
    fun `opprett og hent sak`() {
        withMigratedDb {
            testDataHelper.insertSak(FNR)
            val opprettet: Sak = repo.hentSak(FNR)!!
            val hentetId = repo.hentSak(opprettet.id)!!
            val hentetFnr = repo.hentSak(FNR)!!

            opprettet shouldBe hentetId
            hentetId shouldBe hentetFnr

            opprettet.fnr shouldBe FNR
        }
    }

    @Test
    fun `combination of oppdragId and SakId should be unique`() {
        withMigratedDb {
            testDataHelper.insertSak(FNR)
            val sak: Sak = repo.hentSak(FNR)!!
            shouldThrowExactly<PSQLException> {
                EmbeddedDatabase.instance().withSession {
                    val oppdragId = UUID30.randomUUID()
                    it.run(queryOf("insert into oppdrag (id, opprettet, sakId) values ('$oppdragId', now(), '${sak.id}')").asUpdate)
                }
            }.also {
                it.message shouldContain "duplicate key value violates unique constraint"
            }
        }
    }
}
