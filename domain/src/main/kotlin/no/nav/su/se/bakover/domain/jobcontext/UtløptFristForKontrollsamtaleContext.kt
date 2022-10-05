package no.nav.su.se.bakover.domain.jobcontext

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.AktørId
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.førsteINesteMåned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.ErKontrollNotatMottatt
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtale
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.SakInfo
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class UtløptFristForKontrollsamtaleContext(
    private val id: NameAndLocalDateId,
    private val opprettet: Tidspunkt,
    private val endret: Tidspunkt,
    private val prosessert: Set<UUID>,
    private val ikkeMøtt: Set<UUID>,
    private val feilet: Set<Feilet>,
) : JobContext() {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val MAX_RETRIES = 2

    constructor(
        clock: Clock,
        id: NameAndLocalDateId = genererIdForTidspunkt(clock),
        opprettet: Tidspunkt = Tidspunkt.now(clock),
        endret: Tidspunkt = opprettet,
        prosessert: Set<UUID> = emptySet(),
        ikkeMøtt: Set<UUID> = emptySet(),
        feilet: Set<Feilet> = emptySet(),
    ) : this(id, opprettet, endret, prosessert, ikkeMøtt, feilet)

    override fun id(): NameAndLocalDateId {
        return id
    }

    fun opprettet(): Tidspunkt {
        return opprettet
    }

    fun endret(): Tidspunkt {
        return endret
    }

    fun prosessert(id: UUID, clock: Clock): UtløptFristForKontrollsamtaleContext {
        val fjernHvisFeilet = feilet.find { it.id == id }?.let { feilet.minus(it) } ?: feilet
        return copy(prosessert = prosessert + id, feilet = fjernHvisFeilet, endret = Tidspunkt.now(clock))
    }
    fun prosessert(): Set<UUID> {
        return prosessert
    }

    fun ikkeMøtt(id: UUID, clock: Clock): UtløptFristForKontrollsamtaleContext {
        val fjernHvisFeilet = feilet.find { it.id == id }?.let { feilet.minus(it) } ?: feilet
        return copy(prosessert = prosessert + id, ikkeMøtt = ikkeMøtt + id, feilet = fjernHvisFeilet, endret = Tidspunkt.now(clock))
    }

    fun ikkeMøtt(): Set<UUID> {
        return ikkeMøtt
    }

    fun feilet(id: UUID, feil: String, clock: Clock): UtløptFristForKontrollsamtaleContext {
        return feilet.find { it.id == id }?.let {
            copy(feilet = feilet.minus(it) + it.retried(feil), endret = Tidspunkt.now(clock))
        } ?: copy(feilet = feilet + Feilet(id, 0, feil, null), endret = Tidspunkt.now(clock))
    }

    fun retryLimitReached(id: UUID): Boolean {
        return feilet.find { it.id == id }?.let { it.retries >= MAX_RETRIES } ?: false
    }

    fun prosessertMedFeil(id: UUID, clock: Clock, oppgaveId: OppgaveId): UtløptFristForKontrollsamtaleContext {
        return feilet.find { it.id == id }!!.let {
            copy(prosessert = prosessert + id, feilet = feilet.minus(it) + it.copy(oppgaveId = oppgaveId.toString()), endret = Tidspunkt.now(clock))
        }
    }

    fun møtt(): Set<UUID> {
        return prosessert() - ikkeMøtt() - feilet().map { it.id }.toSet()
    }

    fun feilet(): Set<Feilet> {
        return feilet
    }

    data class Feilet(
        val id: UUID,
        val retries: Int,
        val feil: String,
        val oppgaveId: String?,
    ) {
        fun retried(feil: String): Feilet {
            return copy(retries = retries + 1, feil = feil)
        }
    }

    fun uprosesserte(utestående: () -> List<UUID>): Set<UUID> {
        return (utestående().toSet()).minus(prosessert())
    }

    fun oppsummering(): String {
        return """
            ${"\n"}
            ***********************************
            Oppsummering av jobb: ${id.jobName}, tidspunkt:${Tidspunkt.now()},
            Dato: ${id.date},
            Opprettet: $opprettet,
            Endret: $endret,
            Prosessert: $prosessert,
            Møtt: ${møtt()},
            IkkeMøtt: $ikkeMøtt,
            Feilet: $feilet
            ***********************************
            ${"\n"}
        """.trimIndent()
    }

    fun håndter(
        kontrollsamtale: Kontrollsamtale,
        hentSakInfo: (sakId: UUID) -> Either<KunneIkkeHåndtereUtløptKontrollsamtale, SakInfo>,
        hentKontrollnotatMottatt: (saksnummer: Saksnummer, periode: Periode) -> Either<KunneIkkeHåndtereUtløptKontrollsamtale, ErKontrollNotatMottatt>,
        sessionFactory: SessionFactory,
        opprettStans: (sakId: UUID, fraOgMed: LocalDate, transactionContext: TransactionContext) -> OpprettStansTransactionCallback,
        iverksettStans: (id: UUID, transactionContext: TransactionContext) -> IverksettStansTransactionCallback,
        lagreContext: (context: UtløptFristForKontrollsamtaleContext, transactionContext: TransactionContext) -> Unit,
        clock: Clock,
        lagreKontrollsamtale: (kontrollsamtale: Kontrollsamtale, transactionContext: TransactionContext) -> Unit,
        hentAktørId: (fnr: Fnr) -> Either<KunneIkkeHåndtereUtløptKontrollsamtale, AktørId>,
        opprettOppgave: (oppgaveConfig: OppgaveConfig) -> Either<KunneIkkeHåndtereUtløptKontrollsamtale, OppgaveId>,
    ): UtløptFristForKontrollsamtaleContext {
        return Either.catch {
            hentSakInfo(kontrollsamtale.sakId)
                .fold(
                    {
                        throw FeilVedProsesseringAvKontrollsamtaleException(msg = it.feil)
                    },
                    { sakInfo ->
                        hentKontrollnotatMottatt(
                            sakInfo.saksnummer,
                            kontrollsamtale.forventetMottattNotatPeriode(),
                        ).fold(
                            {
                                throw FeilVedProsesseringAvKontrollsamtaleException(msg = it.feil)
                            },
                            { erKontrollnotatMottatt ->
                                sessionFactory.withTransactionContext { tx ->
                                    when (erKontrollnotatMottatt) {
                                        is ErKontrollNotatMottatt.Ja -> {
                                            kontrollsamtale.settGjennomført(erKontrollnotatMottatt.kontrollnotat.journalpostId)
                                                .fold(
                                                    {
                                                        throw FeilVedProsesseringAvKontrollsamtaleException(msg = it::class.java.toString())
                                                    },
                                                    { møttTilKontrollsamtale ->
                                                        lagreKontrollsamtale(møttTilKontrollsamtale, tx)
                                                        prosessert(møttTilKontrollsamtale.id, clock).also {
                                                            lagreContext(it, tx)
                                                        }
                                                    },
                                                )
                                        }
                                        is ErKontrollNotatMottatt.Nei -> {
                                            kontrollsamtale.settIkkeMøttInnenFrist()
                                                .fold(
                                                    {
                                                        throw FeilVedProsesseringAvKontrollsamtaleException(msg = it::class.java.toString())
                                                    },
                                                    { ikkeMøttKontrollsamtale ->
                                                        lagreKontrollsamtale(ikkeMøttKontrollsamtale, tx)
                                                        opprettStans(sakInfo.sakId, ikkeMøttKontrollsamtale.frist.førsteINesteMåned(), tx).let { opprettCallback ->
                                                            iverksettStans(opprettCallback.revurderingId, tx).let { iverksettCallback ->
                                                                ikkeMøtt(ikkeMøttKontrollsamtale.id, clock).let { ctx ->
                                                                    lagreContext(ctx, tx)
                                                                    iverksettCallback.sendUtbetalingCallback()
                                                                        .getOrHandle {
                                                                            throw FeilVedProsesseringAvKontrollsamtaleException(msg = it::class.java.toString())
                                                                        }
                                                                    opprettCallback.sendStatistikkCallback()
                                                                    iverksettCallback.sendStatistikkCallback()
                                                                    ctx
                                                                }
                                                            }
                                                        }
                                                    },
                                                )
                                        }
                                    }
                                }
                            },
                        )
                    },
                )
        }.fold(
            { error ->
                Either.catch {
                    sessionFactory.withTransactionContext { tx ->
                        feilet(kontrollsamtale.id, error.message!!, clock).let { ctx ->
                            if (retryLimitReached(kontrollsamtale.id)) {
                                val sakInfo = hentSakInfo(kontrollsamtale.sakId)
                                    .getOrHandle { throw FeilVedProsesseringAvKontrollsamtaleException(msg = it.feil) }
                                val aktørId = hentAktørId(sakInfo.fnr)
                                    .getOrHandle { throw FeilVedProsesseringAvKontrollsamtaleException(msg = it.feil) }
                                val oppgaveId = opprettOppgave(
                                    OppgaveConfig.KlarteIkkeÅStanseYtelseVedUtløpAvFristForKontrollsamtale(
                                        saksnummer = sakInfo.saksnummer,
                                        periode = kontrollsamtale.forventetMottattNotatPeriode(),
                                        aktørId = aktørId,
                                        clock = clock,
                                    ),
                                ).getOrHandle { throw FeilVedProsesseringAvKontrollsamtaleException(msg = it.feil) }

                                prosessertMedFeil(kontrollsamtale.id, clock, oppgaveId).also {
                                    logger.info("Maks antall forsøk (${MAX_RETRIES + 1}) for kontrollsamtale:${kontrollsamtale.id} nådd. Gir opp videre prosessering. OppgaveId: $oppgaveId opprettet.")
                                    lagreContext(it, tx)
                                }
                            } else {
                                ctx.also {
                                    lagreContext(ctx, tx)
                                }
                            }
                        }
                    }
                }.fold(
                    {
                        logger.error("Feil: ${it.message!!} ved håndtering av feilet kontrollsamtale: ${kontrollsamtale.id}")
                        this
                    },
                    {
                        logger.error("Feil: ${error.message!!} ved prosessering av kontrollsamtale: ${kontrollsamtale.id}")
                        it
                    },
                )
            },
            {
                it
            },
        )
    }

    private fun Kontrollsamtale.forventetMottattNotatPeriode(): Periode {
        return Periode.create(this.innkallingsdato, this.frist)
    }

    data class KunneIkkeHåndtereUtløptKontrollsamtale(val feil: String)

    private data class FeilVedProsesseringAvKontrollsamtaleException(val msg: String) : RuntimeException(msg)

    data class OpprettStansTransactionCallback(
        val revurderingId: UUID,
        val sendStatistikkCallback: () -> Unit,
    )
    data class IverksettStansTransactionCallback(
        val sendUtbetalingCallback: () -> Either<Any, Utbetalingsrequest>,
        val sendStatistikkCallback: () -> Unit,
    )

    companion object {
        fun genererIdForTidspunkt(clock: Clock): NameAndLocalDateId {
            return NameAndLocalDateId(
                jobName = type().toString(),
                date = LocalDate.now(clock),
            )
        }

        fun type(): Typer {
            return Typer.KontrollsamtaleFristUtløptContext
        }
    }
}
