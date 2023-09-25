package tilbakekreving.infrastructure

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlagHendelse
import tilbakekreving.infrastructure.RåttKravgrunnlagDbJson.Companion.toJson
import java.util.UUID

val MottattKravgrunnlagHendelsestype = Hendelsestype("MOTTATT_KRAVGRUNNLAG")

class KravgrunnlagPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val hendelseRepo: HendelseRepo,
    private val mapper: (råttKravgrunnlag: RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>,
) : KravgrunnlagRepo {

    override fun hentÅpentKravgrunnlagForSak(sakId: UUID): RåttKravgrunnlag? {
        // TODO jah: mottatt_kravgrunnlag er en delt tilstand, men vil på sikt eies av kravgrunnlag-delen av tilbakekreving.
        // TODO jah: Vi skal flytte fremtidige kravgrunnlag til en egen hendelse.
        return sessionFactory.withSession { session ->
            """
                select 
                    kravgrunnlag 
                from 
                    revurdering_tilbakekreving 
                where 
                    tilstand = 'mottatt_kravgrunnlag' 
                    and tilbakekrevingsvedtakForsendelse is null 
                    and sakId=:sakId
                    and opprettet = (SELECT MAX(opprettet) FROM revurdering_tilbakekreving);
            """.trimIndent().hent(
                mapOf("sakId" to sakId),
                session,
            ) {
                it.stringOrNull("kravgrunnlag")?.let { RåttKravgrunnlag(it) }
            }
        }
    }

    override fun hentRåttKravgrunnlag(id: String): RåttKravgrunnlag? {
        return sessionFactory.withSession { session ->
            """
                select 
                    kravgrunnlag 
                from 
                    revurdering_tilbakekreving
            """.trimIndent().hentListe(
                emptyMap(),
                session,
            ) {
                it.stringOrNull("kravgrunnlag")?.let { RåttKravgrunnlag(it) }
            }
        }.firstOrNull {
            if (it == null) {
                false
            } else {
                val kravgrunnlag = mapper(it).getOrElse {
                    throw it
                }
                kravgrunnlag.kravgrunnlagId == id
            }
        }
    }

    override fun hentKravgrunnlag(id: String): Kravgrunnlag? {
        return hentRåttKravgrunnlag(id)?.let {
            mapper(it).getOrElse {
                throw it
            }
        }
    }

    override fun lagreRåKravgrunnlagHendelse(
        hendelse: RåttKravgrunnlagHendelse,
        sessionContext: SessionContext?,
    ) {
        (hendelseRepo as HendelsePostgresRepo).persisterHendelse(
            hendelse = hendelse,
            type = MottattKravgrunnlagHendelsestype,
            data = hendelse.toJson(),
            sessionContext = sessionContext,
        )
    }
}
