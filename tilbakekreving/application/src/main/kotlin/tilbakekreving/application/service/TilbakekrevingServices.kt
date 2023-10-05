package tilbakekreving.application.service

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.person.PersonRepo
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import tilbakekreving.application.service.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingService
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import java.time.Clock

/**
 * Et forsøk på modularisering av [no.nav.su.se.bakover.web.services.Services] der de forskjellige modulene er ansvarlige for å wire opp sine komponenter.
 *
 * Det kan hende vi må splitte denne i en data class + builder.
 */
class TilbakekrevingServices(
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
    private val personRepo: PersonRepo,
    private val personService: PersonService,
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val tilbakekrevingService: TilbakekrevingService,
    private val sakService: SakService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val mapRåttKravgrunnlag: (RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>,
    private val tilgangstyringService: TilbakekrevingsbehandlingTilgangstyringService = TilbakekrevingsbehandlingTilgangstyringService(
        personRepo = personRepo,
        personService = personService,
    ),
    val brevTilbakekrevingsbehandlingService: BrevTilbakekrevingsbehandlingService = BrevTilbakekrevingsbehandlingService(
        tilgangstyring = tilgangstyringService,
        sakService = sakService,
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
        clock = clock,
    ),
    val knyttKravgrunnlagTilSakOgUtbetalingKonsument: KnyttKravgrunnlagTilSakOgUtbetalingKonsument = KnyttKravgrunnlagTilSakOgUtbetalingKonsument(
        kravgrunnlagRepo = kravgrunnlagRepo,
        tilbakekrevingService = tilbakekrevingService,
        sakService = sakService,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
        mapRåttKravgrunnlag = mapRåttKravgrunnlag,
        clock = clock,
        sessionFactory = sessionFactory,
    ),
    val månedsvurderingerTilbakekrevingsbehandlingService: MånedsvurderingerTilbakekrevingsbehandlingService = MånedsvurderingerTilbakekrevingsbehandlingService(
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
        sakService = sakService,
        tilgangstyring = tilgangstyringService,
        clock = clock,
    ),
    val opprettTilbakekrevingsbehandlingService: OpprettTilbakekrevingsbehandlingService = OpprettTilbakekrevingsbehandlingService(
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
        tilgangstyring = tilgangstyringService,
        clock = clock,
        sakService = sakService,
    ),
    val råttKravgrunnlagService: RåttKravgrunnlagService = RåttKravgrunnlagService(
        kravgrunnlagRepo = kravgrunnlagRepo,
        clock = clock,
    ),
    val forhåndsvarsleTilbakekrevingsbehandlingService: ForhåndsvarsleTilbakekrevingsbehandlingService = ForhåndsvarsleTilbakekrevingsbehandlingService(
        tilgangstyring = tilgangstyringService,
        sakService = sakService,
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
    ),
)
