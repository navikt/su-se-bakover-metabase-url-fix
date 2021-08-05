package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.patch
import io.ktor.routing.post
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.BeregnRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.BrevRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.HentRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.IverksettRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeBeregne
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeIverksette
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeOpprette
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeVilkårsvurdere
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.OpprettRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.SendTilAttesteringRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.SimulerRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.UnderkjennRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.VilkårsvurderRequest
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeBehandling
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkePerson
import no.nav.su.se.bakover.web.routes.Feilresponser.kanIkkeHaEpsFradragUtenEps
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.StønadsperiodeJson
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.toUUID
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId
import org.slf4j.LoggerFactory
import java.util.UUID

internal const val behandlingPath = "$sakPath/{sakId}/behandlinger"

internal fun Route.søknadsbehandlingRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    data class OpprettBehandlingBody(val soknadId: String)
    data class WithFritekstBody(val fritekst: String)

    authorize(Brukerrolle.Saksbehandler) {
        post("$sakPath/{sakId}/behandlinger") {
            call.withSakId { sakId ->
                call.withBody<OpprettBehandlingBody> { body ->
                    body.soknadId.toUUID().mapLeft {
                        call.svar(BadRequest.errorJson("soknadId er ikke en gyldig uuid", "ikke_gyldig_uuid"))
                    }.map { søknadId ->
                        søknadsbehandlingService.opprett(OpprettRequest(søknadId))
                            .fold(
                                {
                                    call.svar(
                                        when (it) {
                                            is KunneIkkeOpprette.FantIkkeSøknad -> {
                                                NotFound.errorJson(
                                                    "Fant ikke søknad med id $søknadId",
                                                    "fant_ikke_søknad"
                                                )
                                            }
                                            is KunneIkkeOpprette.SøknadManglerOppgave -> {
                                                InternalServerError.errorJson(
                                                    "Søknad med id $søknadId mangler oppgave",
                                                    "søknad_mangler_oppgave"
                                                )
                                            }
                                            is KunneIkkeOpprette.SøknadHarAlleredeBehandling -> {
                                                BadRequest.errorJson(
                                                    "Søknad med id $søknadId har allerede en behandling",
                                                    "søknad_har_behandling"
                                                )
                                            }
                                            is KunneIkkeOpprette.SøknadErLukket -> {
                                                BadRequest.errorJson(
                                                    "Søknad med id $søknadId er lukket",
                                                    "søknad_er_lukket"
                                                )
                                            }
                                        },
                                    )
                                },
                                {
                                    call.sikkerlogg("Opprettet behandling på sak: $sakId og søknadId: $søknadId")
                                    call.audit(it.fnr, AuditLogEvent.Action.CREATE, it.id)
                                    call.svar(Created.jsonBody(it))
                                },
                            )
                    }
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/stønadsperiode") {

            call.withBehandlingId { behandlingId ->
                call.withBody<StønadsperiodeJson> { body ->
                    body.toStønadsperiode()
                        .mapLeft {
                            call.svar(it)
                        }
                        .flatMap { stønadsperiode ->
                            søknadsbehandlingService.oppdaterStønadsperiode(
                                SøknadsbehandlingService.OppdaterStønadsperiodeRequest(
                                    behandlingId,
                                    stønadsperiode,
                                ),
                            )
                                .mapLeft { error ->
                                    call.svar(
                                        when (error) {
                                            SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling -> fantIkkeBehandling
                                            SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FraOgMedDatoKanIkkeVæreFør2021 -> {
                                                BadRequest.errorJson(
                                                    "En stønadsperiode kan ikke starte før 2021",
                                                    "stønadsperiode_før_2021"
                                                )
                                            }
                                            SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.PeriodeKanIkkeVæreLengreEnn12Måneder -> {
                                                BadRequest.errorJson(
                                                    "En stønadsperiode kan være maks 12 måneder",
                                                    "stønadsperiode_max_12mnd"
                                                )
                                            }
                                        },
                                    )
                                }
                                .map {
                                    call.svar(Created.jsonBody(it))
                                }
                        }
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        get("$behandlingPath/{behandlingId}") {
            call.withBehandlingId { behandlingId ->
                søknadsbehandlingService.hent(HentRequest(behandlingId)).mapLeft {
                    call.svar(NotFound.errorJson("Fant ikke behandling med id $behandlingId", "fant_ikke_behandling"))
                }.map {
                    call.sikkerlogg("Hentet behandling med id $behandlingId")
                    call.audit(it.fnr, AuditLogEvent.Action.ACCESS, it.id)
                    call.svar(OK.jsonBody(it))
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        patch("$behandlingPath/{behandlingId}/informasjon") {
            call.withBehandlingId { behandlingId ->
                call.withBody<BehandlingsinformasjonJson> { body ->
                    if (body.formue != null && !body.formue.harVerdierOgErGyldig()) {
                        return@withBehandlingId call.svar(BadRequest.errorJson("Ugyldige verdier på formue", "ugyldige_verdier_på_formue"))
                    }

                    søknadsbehandlingService.vilkårsvurder(
                        VilkårsvurderRequest(
                            behandlingId = behandlingId,
                            behandlingsinformasjon = behandlingsinformasjonFromJson(body),
                        ),
                    ).fold(
                        {
                            call.svar(
                                when (it) {
                                    KunneIkkeVilkårsvurdere.FantIkkeBehandling -> fantIkkeBehandling
                                    KunneIkkeVilkårsvurdere.HarIkkeEktefelle -> {
                                        BadRequest.errorJson(
                                            "Kan ikke ha formue for eps når søker ikke har eps",
                                            "har_ikke_ektefelle"
                                        )
                                    }
                                },
                            )
                        },
                        {
                            call.sikkerlogg("Oppdaterte behandlingsinformasjon med behandlingsid $behandlingId")
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                            call.svar(OK.jsonBody(it))
                        },
                    )
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/beregn") {
            data class Body(
                val fradrag: List<FradragJson>,
                val begrunnelse: String?,
            ) {
                fun toDomain(behandlingId: UUID): Either<Resultat, BeregnRequest> {
                    return BeregnRequest(
                        behandlingId = behandlingId,
                        fradrag = fradrag.map { fradrag ->
                            BeregnRequest.FradragRequest(
                                periode = fradrag.periode?.toPeriode()?.getOrHandle { feilResultat ->
                                    return feilResultat.left()
                                },
                                type = fradrag.type.let {
                                    Fradragstype.tryParse(it).getOrHandle {
                                        return BadRequest.errorJson("Ugyldig fradragstype", "ugyldig_fradragstype")
                                            .left()
                                    }
                                },
                                månedsbeløp = fradrag.beløp,
                                utenlandskInntekt = fradrag.utenlandskInntekt?.toUtenlandskInntekt()
                                    ?.getOrHandle { feilResultat ->
                                        return feilResultat.left()
                                    },
                                tilhører = fradrag.tilhører.let { FradragTilhører.valueOf(it) },

                            )
                        },
                        begrunnelse = begrunnelse,
                    ).right()
                }
            }

            call.withBehandlingId { behandlingId ->
                call.withBody<Body> { body ->
                    body.toDomain(behandlingId)
                        .mapLeft { call.svar(it) }
                        .map { serviceCommand ->
                            søknadsbehandlingService.beregn(serviceCommand)
                                .mapLeft { kunneIkkeBeregne ->
                                    val resultat = when (kunneIkkeBeregne) {
                                        KunneIkkeBeregne.FantIkkeBehandling -> fantIkkeBehandling
                                        KunneIkkeBeregne.IkkeLovMedFradragUtenforPerioden -> BadRequest.errorJson(
                                            "Ikke lov med fradrag utenfor perioden",
                                            "ikke_lov_med_fradrag_utenfor_perioden",
                                        )
                                        KunneIkkeBeregne.UgyldigFradragstype -> BadRequest.errorJson(
                                            "Ugyldig fradragstype",
                                            "ugyldig_fradragstype",
                                        )
                                        KunneIkkeBeregne.HarIkkeEktefelle -> kanIkkeHaEpsFradragUtenEps
                                    }
                                    call.svar(resultat)
                                }.map { behandling ->
                                    call.sikkerlogg("Beregner på søknadsbehandling med id $behandlingId")
                                    call.audit(behandling.fnr, AuditLogEvent.Action.UPDATE, behandling.id)
                                    call.svar(Created.jsonBody(behandling))
                                }
                        }
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        suspend fun lagBrevutkast(call: ApplicationCall, req: BrevRequest) =
            søknadsbehandlingService.brev(req)
                .fold(
                    {
                        val resultat = when (it) {
                            is KunneIkkeLageBrev.KunneIkkeLagePDF -> {
                                InternalServerError.errorJson("Kunne ikke lage brev", "kunne_ikke_lage_pdf")
                            }
                            is KunneIkkeLageBrev.KanIkkeLageBrevutkastForStatus -> {
                                BadRequest.errorJson(
                                    "Kunne ikke lage brev for behandlingstatus: ${it.status}",
                                    "kunne_ikke_lage_brevutkast"
                                )
                            }
                            is KunneIkkeLageBrev.FantIkkePerson -> fantIkkePerson
                            is KunneIkkeLageBrev.FikkIkkeHentetSaksbehandlerEllerAttestant -> {
                                InternalServerError.errorJson(
                                    "Klarte ikke hente informasjon om saksbehandler og/eller attestant",
                                    "feil_ved_henting_av_saksbehandler_eller_attestant"
                                )
                            }
                            is KunneIkkeLageBrev.KunneIkkeFinneGjeldendeUtbetaling -> {
                                InternalServerError.errorJson(
                                    "Kunne ikke hente gjeldende utbetaling",
                                    "finner_ikke_utbetaling"
                                )
                            }
                        }
                        call.svar(resultat)
                    },
                    {
                        call.sikkerlogg("Hentet brev for behandling med id ${req.behandling.id}")
                        call.audit(req.behandling.fnr, AuditLogEvent.Action.ACCESS, req.behandling.id)
                        call.respondBytes(it, ContentType.Application.Pdf)
                    },
                )

        post("$behandlingPath/{behandlingId}/vedtaksutkast") {
            call.withBehandlingId { behandlingId ->
                call.withBody<WithFritekstBody> { body ->
                    søknadsbehandlingService.hent(HentRequest(behandlingId))
                        .fold(
                            { call.svar(fantIkkeBehandling) },
                            { lagBrevutkast(call, BrevRequest.MedFritekst(it, body.fritekst)) },
                        )
                }
            }
        }
        get("$behandlingPath/{behandlingId}/vedtaksutkast") {
            call.withBehandlingId { behandlingId ->
                søknadsbehandlingService.hent(HentRequest(behandlingId))
                    .fold(
                        { call.svar(fantIkkeBehandling) },
                        { lagBrevutkast(call, BrevRequest.UtenFritekst(it)) },
                    )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/simuler") {
            call.withBehandlingId { behandlingId ->
                søknadsbehandlingService.simuler(
                    SimulerRequest(
                        behandlingId = behandlingId,
                        saksbehandler = Saksbehandler(call.suUserContext.navIdent),
                    ),
                ).fold(
                    {
                        val resultat = when (it) {
                            KunneIkkeSimulereBehandling.KunneIkkeSimulere -> {
                                InternalServerError.errorJson("Kunne ikke gjennomføre simulering", "kunne_ikke_simulere")
                            }
                            KunneIkkeSimulereBehandling.FantIkkeBehandling -> fantIkkeBehandling
                        }
                        call.svar(resultat)
                    },
                    {
                        call.sikkerlogg("Oppdatert simulering for behandling med id $behandlingId")
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, behandlingId)
                        call.svar(OK.jsonBody(it))
                    },
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/tilAttestering") {
            call.withBehandlingId { behandlingId ->
                call.withSakId {
                    call.withBody<WithFritekstBody> { body ->
                        val saksBehandler = Saksbehandler(call.suUserContext.navIdent)
                        søknadsbehandlingService.sendTilAttestering(
                            SendTilAttesteringRequest(
                                behandlingId = behandlingId,
                                saksbehandler = saksBehandler,
                                fritekstTilBrev = body.fritekst,
                            ),
                        ).fold(
                            {
                                val resultat = when (it) {
                                    KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave -> {
                                        InternalServerError.errorJson(
                                            "Kunne ikke opprette oppgave for attestering",
                                            "kunne_ikke_opprette_oppgave"
                                        )
                                    }
                                    KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId -> {
                                        InternalServerError.errorJson("Kunne ikke finne person", "kunne_ikke_finne_aktørid")
                                    }
                                    KunneIkkeSendeTilAttestering.FantIkkeBehandling -> fantIkkeBehandling
                                }
                                call.svar(resultat)
                            },
                            {
                                call.sikkerlogg("Sendte behandling med id $behandlingId til attestering")
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                call.svar(OK.jsonBody(it))
                            },
                        )
                    }
                }
            }
        }
    }

    authorize(Brukerrolle.Attestant) {

        fun kunneIkkeIverksetteMelding(value: KunneIkkeIverksette): Resultat {
            return when (value) {
                is KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> {
                    Forbidden.errorJson("Attestant og saksbehandler kan ikke være samme person", "attestant_samme_som_saksbehandler")
                }
                is KunneIkkeIverksette.KunneIkkeUtbetale -> {
                    InternalServerError.errorJson("Kunne ikke utføre utbetaling", "kunne_ikke_utbetale")
                }
                is KunneIkkeIverksette.KunneIkkeKontrollsimulere -> {
                    InternalServerError.errorJson("Kunne ikke utføre kontrollsimulering", "kunne_ikke_kontrollsimulere")
                }
                is KunneIkkeIverksette.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> {
                    InternalServerError.errorJson(
                        "Oppdaget inkonsistens mellom tidligere utført simulering og kontrollsimulering. Ny simulering må utføres og kontrolleres før iverksetting kan gjennomføres",
                        "kontrollsimulering_ulik_saksbehandlers_simulering"
                    )
                }
                is KunneIkkeIverksette.KunneIkkeJournalføreBrev -> {
                    InternalServerError.errorJson("Feil ved journalføring av vedtaksbrev", "kunne_ikke_journalføre_brev")
                }
                is KunneIkkeIverksette.FantIkkeBehandling -> fantIkkeBehandling
                is KunneIkkeIverksette.FantIkkePerson -> fantIkkePerson
                is KunneIkkeIverksette.FikkIkkeHentetSaksbehandlerEllerAttestant -> {
                    InternalServerError.errorJson(
                        "Klarte ikke hente informasjon om saksbehandler og/eller attestant",
                        "feil_ved_henting_av_saksbehandler_eller_attestant"
                    )
                }
            }
        }

        patch("$behandlingPath/{behandlingId}/iverksett") {
            call.withBehandlingId { behandlingId ->

                val navIdent = call.suUserContext.navIdent

                søknadsbehandlingService.iverksett(
                    IverksettRequest(
                        behandlingId = behandlingId,
                        attestering = Attestering.Iverksatt(Attestant(navIdent), Tidspunkt.now()),
                    ),
                ).fold(
                    {
                        call.svar(kunneIkkeIverksetteMelding(it))
                    },
                    {
                        call.sikkerlogg("Iverksatte behandling med id: $behandlingId")
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                        call.svar(OK.jsonBody(it))
                    },
                )
            }
        }
    }

    data class UnderkjennBody(
        val grunn: String,
        val kommentar: String,
    ) {
        fun valid() = enumContains<Attestering.Underkjent.Grunn>(grunn) && kommentar.isNotBlank()
    }

    authorize(Brukerrolle.Attestant) {
        patch("$behandlingPath/{behandlingId}/underkjenn") {
            val navIdent = call.suUserContext.navIdent

            call.withBehandlingId { behandlingId ->
                Either.catch { deserialize<UnderkjennBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig behandling-body: ", it)
                        call.svar(Feilresponser.ugyldigBody)
                    },
                    ifRight = { body ->
                        if (body.valid()) {
                            søknadsbehandlingService.underkjenn(
                                UnderkjennRequest(
                                    behandlingId = behandlingId,
                                    attestering = Attestering.Underkjent(
                                        attestant = Attestant(navIdent),
                                        grunn = Attestering.Underkjent.Grunn.valueOf(body.grunn),
                                        kommentar = body.kommentar,
                                        opprettet = Tidspunkt.now()
                                    ),
                                ),
                            ).fold(
                                ifLeft = {
                                    val resultat = when (it) {
                                        KunneIkkeUnderkjenne.FantIkkeBehandling -> fantIkkeBehandling
                                        KunneIkkeUnderkjenne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> {
                                            Forbidden.errorJson(
                                                "Attestant og saksbehandler kan ikke være samme person",
                                                "attestant_samme_som_saksbehandler"
                                            )
                                        }
                                        KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave -> {
                                            InternalServerError.errorJson(
                                                "Oppgaven er lukket, men vi kunne ikke opprette oppgave. Prøv igjen senere.",
                                                "kunne_ikke_opprette_oppgave"
                                            )
                                        }
                                        KunneIkkeUnderkjenne.FantIkkeAktørId -> {
                                            InternalServerError.errorJson(
                                                "Fant ikke aktørid som er knyttet til tokenet",
                                                "fant_ikke_aktørid"
                                            )
                                        }
                                    }
                                    call.svar(resultat)
                                },
                                ifRight = {
                                    call.sikkerlogg("Underkjente behandling med id: $behandlingId")
                                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                    call.svar(OK.jsonBody(it))
                                },
                            )
                        } else {
                            call.svar(BadRequest.errorJson("Må angi en begrunnelse", "mangler_begrunnelse"))
                        }
                    },
                )
            }
        }
    }
}
