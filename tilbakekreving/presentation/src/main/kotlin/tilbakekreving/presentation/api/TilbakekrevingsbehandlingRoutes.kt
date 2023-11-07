package tilbakekreving.presentation.api

import io.ktor.server.routing.Route
import tilbakekreving.application.service.avbrutt.AvbrytTilbakekrevingsbehandlingService
import tilbakekreving.application.service.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingService
import tilbakekreving.application.service.forhåndsvarsel.ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService
import tilbakekreving.application.service.forhåndsvarsel.VisUtsendtForhåndsvarselbrevForTilbakekrevingService
import tilbakekreving.application.service.iverksett.IverksettTilbakekrevingService
import tilbakekreving.application.service.opprett.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.application.service.tilAttestering.TilbakekrevingsbehandlingTilAttesteringService
import tilbakekreving.application.service.underkjenn.UnderkjennTilbakekrevingsbehandlingService
import tilbakekreving.application.service.vurder.BrevTilbakekrevingsbehandlingService
import tilbakekreving.application.service.vurder.ForhåndsvisVedtaksbrevTilbakekrevingsbehandlingService
import tilbakekreving.application.service.vurder.MånedsvurderingerTilbakekrevingsbehandlingService
import tilbakekreving.presentation.api.avslutt.avbrytTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.api.forhåndsvarsel.forhåndsvarsleTilbakekrevingRoute
import tilbakekreving.presentation.api.forhåndsvarsel.visForhåndsvarselTilbakekrevingsbrev
import tilbakekreving.presentation.api.forhåndsvarsel.visUtsendtForhåndsvarselbrevForTilbakekrevingRoute
import tilbakekreving.presentation.api.iverksett.iverksettTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.api.opprett.opprettTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.api.tilAttestering.tilAttesteringTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.api.underkjenn.underkjennTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.api.vedtaksbrev.vedtaksbrevTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.api.vurder.vurderTilbakekrevingsbehandlingRoute

internal const val TILBAKEKREVING_PATH = "saker/{sakId}/tilbakekreving"

fun Route.tilbakekrevingRoutes(
    opprettTilbakekrevingsbehandlingService: OpprettTilbakekrevingsbehandlingService,
    månedsvurderingerTilbakekrevingsbehandlingService: MånedsvurderingerTilbakekrevingsbehandlingService,
    brevTilbakekrevingsbehandlingService: BrevTilbakekrevingsbehandlingService,
    forhåndsvisVedtaksbrevTilbakekrevingsbehandlingService: ForhåndsvisVedtaksbrevTilbakekrevingsbehandlingService,
    forhåndsvarsleTilbakekrevingsbehandlingService: ForhåndsvarsleTilbakekrevingsbehandlingService,
    forhåndsvisForhåndsvarselTilbakekrevingsbehandlingService: ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService,
    visUtsendtForhåndsvarselbrevForTilbakekrevingService: VisUtsendtForhåndsvarselbrevForTilbakekrevingService,
    tilbakekrevingsbehandlingTilAttesteringService: TilbakekrevingsbehandlingTilAttesteringService,
    underkjennTilbakekrevingsbehandlingService: UnderkjennTilbakekrevingsbehandlingService,
    iverksettTilbakekrevingService: IverksettTilbakekrevingService,
    avbrytTilbakekrevingsbehandlingService: AvbrytTilbakekrevingsbehandlingService,
) {
    this.opprettTilbakekrevingsbehandlingRoute(opprettTilbakekrevingsbehandlingService)
    this.vurderTilbakekrevingsbehandlingRoute(månedsvurderingerTilbakekrevingsbehandlingService)
    this.vedtaksbrevTilbakekrevingsbehandlingRoute(brevTilbakekrevingsbehandlingService)
    this.vedtaksbrevTilbakekrevingsbehandlingRoute(forhåndsvisVedtaksbrevTilbakekrevingsbehandlingService)
    this.forhåndsvarsleTilbakekrevingRoute(forhåndsvarsleTilbakekrevingsbehandlingService)
    this.visForhåndsvarselTilbakekrevingsbrev(forhåndsvisForhåndsvarselTilbakekrevingsbehandlingService)
    this.tilAttesteringTilbakekrevingsbehandlingRoute(tilbakekrevingsbehandlingTilAttesteringService)
    this.visUtsendtForhåndsvarselbrevForTilbakekrevingRoute(visUtsendtForhåndsvarselbrevForTilbakekrevingService)
    this.underkjennTilbakekrevingsbehandlingRoute(underkjennTilbakekrevingsbehandlingService)
    this.iverksettTilbakekrevingsbehandlingRoute(iverksettTilbakekrevingService)
    this.avbrytTilbakekrevingsbehandlingRoute(avbrytTilbakekrevingsbehandlingService)
}
