package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import no.nav.su.se.bakover.common.Tidspunkt.Companion.now
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics.UnderkjentHandlinger
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

internal class BehandlingServiceImpl(
    private val behandlingRepo: BehandlingRepo,
    private val hendelsesloggRepo: HendelsesloggRepo,
    private val utbetalingService: UtbetalingService,
    private val oppgaveService: OppgaveService,
    private val søknadService: SøknadService,
    private val søknadRepo: SøknadRepo, // TODO use services or repos? probably services
    private val personOppslag: PersonOppslag,
    private val brevService: BrevService,
    private val behandlingMetrics: BehandlingMetrics
) : BehandlingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentBehandling(behandlingId: UUID): Either<FantIkkeBehandling, Behandling> {
        return behandlingRepo.hentBehandling(behandlingId)?.right() ?: FantIkkeBehandling.left()
    }

    override fun underkjenn(
        behandlingId: UUID,
        attestant: NavIdentBruker.Attestant,
        begrunnelse: String
    ): Either<KunneIkkeUnderkjenneBehandling, Behandling> {
        return hentBehandling(behandlingId).mapLeft {
            log.info("Kunne ikke underkjenne ukjent behandling $behandlingId")
            KunneIkkeUnderkjenneBehandling.FantIkkeBehandling
        }.flatMap { behandling ->
            behandling.underkjenn(begrunnelse, attestant)
                .mapLeft {
                    log.warn("Kunne ikke underkjenne behandling siden attestant og saksbehandler var samme person")
                    KunneIkkeUnderkjenneBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
                }
                .map {
                    val aktørId: AktørId = personOppslag.aktørId(behandling.fnr).getOrElse {
                        log.error("Kunne ikke underkjenne behandling; fant ikke aktør id")
                        return KunneIkkeUnderkjenneBehandling.FantIkkeAktørId.left()
                    }

                    val journalpostId: JournalpostId = behandling.søknad.journalpostId
                    val eksisterendeOppgaveId = behandling.oppgaveId()
                    val nyOppgaveId = oppgaveService.opprettOppgave(
                        OppgaveConfig.Saksbehandling(
                            journalpostId = journalpostId,
                            sakId = behandling.sakId,
                            aktørId = aktørId,
                            tilordnetRessurs = behandling.saksbehandler()
                        )
                    ).getOrElse {
                        log.error("Behandling $behandlingId ble ikke underkjent. Klarte ikke opprette behandlingsoppgave")
                        return@underkjenn KunneIkkeUnderkjenneBehandling.KunneIkkeOppretteOppgave.left()
                    }.also {
                        behandlingMetrics.incrementUnderkjentCounter(UnderkjentHandlinger.OPPRETTET_OPPGAVE)
                    }
                    behandling.oppdaterOppgaveId(nyOppgaveId)
                    behandlingRepo.oppdaterAttestant(behandlingId, attestant)
                    behandlingRepo.oppdaterOppgaveId(behandling.id, nyOppgaveId)
                    behandlingRepo.oppdaterBehandlingStatus(it.id, it.status())
                    log.info("Behandling $behandlingId ble underkjent. Opprettet behandlingsoppgave $nyOppgaveId")
                    hendelsesloggRepo.oppdaterHendelseslogg(it.hendelseslogg)
                    behandlingMetrics.incrementUnderkjentCounter(UnderkjentHandlinger.PERSISTERT)
                    oppgaveService.lukkOppgave(eksisterendeOppgaveId)
                        .mapLeft {
                            log.error("Kunne ikke lukke attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av behandlingen. Dette må gjøres manuelt.")
                        }.map {
                            log.info("Lukket attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av behandlingen")
                            behandlingMetrics.incrementUnderkjentCounter(UnderkjentHandlinger.LUKKET_OPPGAVE)
                        }
                    behandling
                }
        }
    }

    // TODO need to define responsibilities for domain and services.
    override fun oppdaterBehandlingsinformasjon(
        behandlingId: UUID,
        behandlingsinformasjon: Behandlingsinformasjon
    ): Behandling {
        return behandlingRepo.hentBehandling(behandlingId)!!
            .oppdaterBehandlingsinformasjon(behandlingsinformasjon) // invoke first to perform state-check
            .let {
                behandlingRepo.slettBeregning(behandlingId)
                behandlingRepo.oppdaterBehandlingsinformasjon(behandlingId, it.behandlingsinformasjon())
                behandlingRepo.oppdaterBehandlingStatus(behandlingId, it.status())
                it
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun opprettBeregning(
        saksbehandler: NavIdentBruker.Saksbehandler,
        behandlingId: UUID,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<Fradrag>
    ): Either<KunneIkkeBeregne, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId) ?: return KunneIkkeBeregne.FantIkkeBehandling.left()

        return behandling.opprettBeregning(saksbehandler, fraOgMed, tilOgMed, fradrag) // invoke first to perform state-check
            .mapLeft { KunneIkkeBeregne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson }
            .map {
                behandlingRepo.leggTilBeregning(it.id, it.beregning()!!)
                behandlingRepo.oppdaterBehandlingStatus(behandlingId, it.status())
                it
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun simuler(
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeSimulereBehandling, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)
            ?: return KunneIkkeSimulereBehandling.FantIkkeBehandling.left()

        return behandling.leggTilSimulering(saksbehandler) {
            utbetalingService.simulerUtbetaling(behandling.sakId, saksbehandler, behandling.beregning()!!)
                .map { it.simulering }.orNull()
        }.mapLeft {
            when (it) {
                Behandling.KunneIkkeLeggeTilSimulering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> KunneIkkeSimulereBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
                Behandling.KunneIkkeLeggeTilSimulering.KunneIkkeSimulere -> KunneIkkeSimulereBehandling.KunneIkkeSimulere
            }
        }.map { simulertBehandling ->
            behandlingRepo.leggTilSimulering(behandlingId, simulertBehandling.simulering()!!)
            behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
            behandlingRepo.hentBehandling(behandlingId)!!
        }
    }

    // TODO need to define responsibilities for domain and services.
    override fun sendTilAttestering(
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSendeTilAttestering, Behandling> {

        val behandlingTilAttestering: Behandling =
            behandlingRepo.hentBehandling(behandlingId).rightIfNotNull {
                return KunneIkkeSendeTilAttestering.FantIkkeBehandling.left()
            }.flatMap {
                it.sendTilAttestering(saksbehandler)
            }.getOrElse {
                return KunneIkkeSendeTilAttestering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }

        val aktørId = personOppslag.aktørId(behandlingTilAttestering.fnr).getOrElse {
            log.error("Fant ikke aktør-id med for fødselsnummer : ${behandlingTilAttestering.fnr}")
            return KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId.left()
        }

        val eksisterendeOppgaveId: OppgaveId = behandlingTilAttestering.oppgaveId()

        val nyOppgaveId: OppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.Attestering(
                behandlingTilAttestering.sakId,
                aktørId = aktørId,
                // Første gang den sendes til attestering er attestant null, de påfølgende gangene vil den være attestanten som har underkjent.
                tilordnetRessurs = behandlingTilAttestering.attestant()
            )
        ).getOrElse {
            log.error("Kunne ikke opprette Attesteringsoppgave. Avbryter handlingen.")
            return KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()
        }.also {
            behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.OPPRETTET_OPPGAVE)
        }
        behandlingTilAttestering.oppdaterOppgaveId(nyOppgaveId)
        behandlingRepo.oppdaterOppgaveId(behandlingTilAttestering.id, nyOppgaveId)
        behandlingRepo.settSaksbehandler(behandlingId, saksbehandler)
        behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandlingTilAttestering.status())
        behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.PERSISTERT)

        oppgaveService.lukkOppgave(eksisterendeOppgaveId).map {
            behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.LUKKET_OPPGAVE)
        }.mapLeft {
            log.error("Klarte ikke å lukke oppgave. kall til oppgave for oppgaveId ${behandlingTilAttestering.oppgaveId()} feilet")
        }
        return behandlingTilAttestering.right()
    }

    // TODO need to define responsibilities for domain and services.
    // TODO refactor the beast
    override fun iverksett(
        behandlingId: UUID,
        attestant: NavIdentBruker.Attestant
    ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)
            ?: return KunneIkkeIverksetteBehandling.FantIkkeBehandling.left()

        return behandling.iverksett(attestant) // invoke first to perform state-check
            .mapLeft {
                KunneIkkeIverksetteBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            }
            .map { iverksattBehandling ->
                return when (iverksattBehandling.status()) {
                    Behandling.BehandlingsStatus.IVERKSATT_AVSLAG -> iverksettAvslag(
                        behandling = iverksattBehandling,
                        attestant = attestant
                    )
                    Behandling.BehandlingsStatus.IVERKSATT_INNVILGET -> iverksettInnvilgning(
                        behandling = iverksattBehandling,
                        attestant = attestant,
                        behandlingId = behandlingId
                    )
                    else -> throw Behandling.TilstandException(
                        state = iverksattBehandling.status(),
                        operation = iverksattBehandling::iverksett.toString()
                    )
                }
            }
    }

    private fun iverksettAvslag(
        behandling: Behandling,
        attestant: NavIdentBruker.Attestant
    ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {

        val journalpostId = brevService.journalførBrev(LagBrevRequest.AvslagsVedtak(behandling), behandling.sakId)
            .map {
                behandling.oppdaterIverksattJournalpostId(it)
                it
            }
            .getOrElse {
                log.error("Behandling ${behandling.id} ble ikke avslått siden vi ikke klarte journalføre. Saksbehandleren må prøve på nytt.")
                return KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev.left()
            }
        behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.JOURNALFØRT)

        behandlingRepo.oppdaterIverksattJournalpostId(behandling.id, journalpostId)
        behandlingRepo.oppdaterAttestant(behandling.id, attestant)
        behandlingRepo.oppdaterBehandlingStatus(behandling.id, behandling.status())
        log.info("Iversatt avslag for behandling ${behandling.id} med journalpost $journalpostId")
        behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)

        val brevResultat = brevService.distribuerBrev(journalpostId)
            .mapLeft {
                log.error("Kunne ikke bestille brev ved avslag for behandling ${behandling.id}. Dette må gjøres manuelt.")
                IverksattBehandling.MedMangler.KunneIkkeDistribuereBrev(behandling)
            }
            .map {
                behandling.oppdaterIverksattBrevbestillingId(it)
                behandlingRepo.oppdaterIverksattBrevbestillingId(behandling.id, it)
                log.info("Bestilt avslagsbrev for behandling ${behandling.id} med bestillingsid $it")
                behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV)
                IverksattBehandling.UtenMangler(behandling)
            }

        val oppgaveResultat = oppgaveService.lukkOppgave(behandling.oppgaveId())
            .mapLeft {
                log.error("Kunne ikke lukke oppgave ved avslag for behandling ${behandling.id}. Dette må gjøres manuelt.")
                IverksattBehandling.MedMangler.KunneIkkeLukkeOppgave(behandling)
            }
            .map {
                log.info("Lukket oppgave ${behandling.oppgaveId()} ved avslag for behandling ${behandling.id}")
                // TODO jah: Vurder behandling.oppdaterOppgaveId(null), men den kan ikke være null atm.
                behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.LUKKET_OPPGAVE)
                IverksattBehandling.UtenMangler(behandling)
            }

        return brevResultat.flatMap { oppgaveResultat }.fold(
            { it.right() },
            { it.right() }
        )
    }

    private fun iverksettInnvilgning(
        behandling: Behandling,
        attestant: NavIdentBruker.Attestant,
        behandlingId: UUID
    ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {

        return utbetalingService.utbetal(
            sakId = behandling.sakId,
            attestant = attestant,
            beregning = behandling.beregning()!!,
            simulering = behandling.simulering()!!
        ).mapLeft {
            log.error("Kunne ikke innvilge behandling ${behandling.id} siden utbetaling feilet. Feiltype: $it")
            when (it) {
                KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
                KunneIkkeUtbetale.Protokollfeil -> KunneIkkeIverksetteBehandling.KunneIkkeUtbetale
                KunneIkkeUtbetale.KunneIkkeSimulere -> KunneIkkeIverksetteBehandling.KunneIkkeKontrollsimulere
            }
        }.flatMap { oversendtUtbetaling ->
            behandlingRepo.leggTilUtbetaling(
                behandlingId = behandlingId,
                utbetalingId = oversendtUtbetaling.id
            )
            behandlingRepo.oppdaterAttestant(behandlingId, attestant)
            behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
            log.info("Behandling ${behandling.id} innvilget med utbetaling ${oversendtUtbetaling.id}")
            behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)

            val journalføringOgBrevResultat =
                brevService.journalførBrev(LagBrevRequest.InnvilgetVedtak(behandling), behandling.sakId)
                    .mapLeft {
                        log.error("Journalføring av iverksettingsbrev feilet for behandling ${behandling.id}. Dette må gjøres manuelt.")
                        IverksattBehandling.MedMangler.KunneIkkeJournalføreBrev(behandling)
                    }
                    .flatMap {
                        behandling.oppdaterIverksattJournalpostId(it)
                        behandlingRepo.oppdaterIverksattJournalpostId(behandling.id, it)
                        log.info("Journalført iverksettingsbrev $it for behandling ${behandling.id}")
                        behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.JOURNALFØRT)
                        brevService.distribuerBrev(it)
                            .mapLeft {
                                log.error("Bestilling av iverksettingsbrev feilet for behandling ${behandling.id}. Dette må gjøres manuelt.")
                                IverksattBehandling.MedMangler.KunneIkkeDistribuereBrev(behandling)
                            }
                            .map { brevbestillingId ->
                                behandling.oppdaterIverksattBrevbestillingId(brevbestillingId)
                                behandlingRepo.oppdaterIverksattBrevbestillingId(behandling.id, brevbestillingId)
                                log.info("Bestilt iverksettingsbrev $brevbestillingId for behandling ${behandling.id}")
                                behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
                                IverksattBehandling.UtenMangler(behandling)
                            }
                    }

            val oppgaveResultat = oppgaveService.lukkOppgave(behandling.oppgaveId())
                .mapLeft {
                    log.error("Kunne ikke lukke oppgave ved innvilgelse for behandling ${behandling.id}. Dette må gjøres manuelt.")
                    IverksattBehandling.MedMangler.KunneIkkeLukkeOppgave(behandling)
                }
                .map {
                    log.info("Lukket oppgave ${behandling.oppgaveId()} ved innvilgelse for behandling ${behandling.id}")
                    // TODO jah: Vurder behandling.oppdaterOppgaveId(null), men den kan ikke være null atm.
                    behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
                    IverksattBehandling.UtenMangler(behandling)
                }

            return journalføringOgBrevResultat.flatMap { oppgaveResultat }.fold(
                { it.right() },
                { it.right() }
            )
        }
    }

    override fun opprettSøknadsbehandling(
        søknadId: UUID
    ): Either<KunneIkkeOppretteSøknadsbehandling, Behandling> {
        val søknad = søknadService.hentSøknad(søknadId).getOrElse {
            return KunneIkkeOppretteSøknadsbehandling.FantIkkeSøknad.left()
        }
        if (søknad is Søknad.Lukket) {
            return KunneIkkeOppretteSøknadsbehandling.SøknadErLukket.left()
        }
        if (søknad !is Søknad.Journalført.MedOppgave) {
            // TODO Prøv å opprette oppgaven hvis den mangler?
            return KunneIkkeOppretteSøknadsbehandling.SøknadManglerOppgave.left()
        }
        if (søknadRepo.harSøknadPåbegyntBehandling(søknad.id)) {
            // Dersom man legger til avslutting av behandlinger, må denne spørringa spesifiseres.
            return KunneIkkeOppretteSøknadsbehandling.SøknadHarAlleredeBehandling.left()
        }
        val nySøknadsbehandling = NySøknadsbehandling(
            id = UUID.randomUUID(),
            opprettet = now(),
            sakId = søknad.sakId,
            søknadId = søknad.id,
            oppgaveId = søknad.oppgaveId
        )
        behandlingRepo.opprettSøknadsbehandling(
            nySøknadsbehandling
        )
        return behandlingRepo.hentBehandling(nySøknadsbehandling.id)!!.right()
    }

    override fun lagBrevutkast(behandlingId: UUID): Either<KunneIkkeLageBrevutkast, ByteArray> {
        return hentBehandling(behandlingId)
            .mapLeft { KunneIkkeLageBrevutkast.FantIkkeBehandling }
            .flatMap { behandling ->
                brevService.lagBrev(lagBrevRequest(behandling))
                    .mapLeft { KunneIkkeLageBrevutkast.KunneIkkeLageBrev }
                    .map { it }
            }
    }

    private fun lagBrevRequest(behandling: Behandling) = when (behandling.erInnvilget()) {
        true -> LagBrevRequest.InnvilgetVedtak(behandling)
        false -> LagBrevRequest.AvslagsVedtak(behandling)
    }
}
