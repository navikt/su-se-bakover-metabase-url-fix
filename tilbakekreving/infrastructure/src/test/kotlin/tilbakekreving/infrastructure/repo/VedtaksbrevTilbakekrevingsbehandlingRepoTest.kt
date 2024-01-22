package tilbakekreving.infrastructure.repo

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

/**
 * Disse testene dekker også hentForSak og hent(hendelseId)
 */
class VedtaksbrevTilbakekrevingsbehandlingRepoTest {

    @Test
    fun `kan vurdere tilbakekrevingsbehandling`() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)

            val (sak, _, _, _, hendelser) = testDataHelper.tilbakekreving.persisterVedtaksbrevTilbakekrevingsbehandlingHendelse()

            val actual = testDataHelper.tilbakekreving.tilbakekrevingHendelseRepo.hentForSak(sak.id)
            testDataHelper.kravgrunnlagPostgresRepo.hentKravgrunnlagPåSakHendelser(sak.id).also {
                it.size shouldBe 1
                it.detaljerSortert.first().kravgrunnlag shouldBe sak.uteståendeKravgrunnlag
            }
            actual shouldBe hendelser
        }
    }
}
