package tilbakekreving.presentation.api

import io.ktor.server.routing.Route
import tilbakekreving.application.service.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingService
import tilbakekreving.application.service.forhåndsvarsel.ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService
import tilbakekreving.application.service.iverksett.IverksettTilbakekrevingService
import tilbakekreving.application.service.opprett.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.application.service.tilAttestering.TilbakekrevingsbehandlingTilAttesteringService
import tilbakekreving.application.service.vurder.BrevTilbakekrevingsbehandlingService
import tilbakekreving.application.service.vurder.MånedsvurderingerTilbakekrevingsbehandlingService
import tilbakekreving.presentation.api.forhåndsvarsel.forhåndsvarsleTilbakekrevingRoute
import tilbakekreving.presentation.api.forhåndsvarsel.visForhåndsvarselTilbakekrevingsbrev
import tilbakekreving.presentation.api.iverksett.iverksettTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.api.opprett.opprettTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.api.tilAttestering.tilAttesteringTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.api.vurder.brevTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.api.vurder.månedsvurderingerTilbakekrevingsbehandlingRoute

internal const val TILBAKEKREVING_PATH = "saker/{sakId}/tilbakekreving"

fun Route.tilbakekrevingRoutes(
    opprettTilbakekrevingsbehandlingService: OpprettTilbakekrevingsbehandlingService,
    månedsvurderingerTilbakekrevingsbehandlingService: MånedsvurderingerTilbakekrevingsbehandlingService,
    brevTilbakekrevingsbehandlingService: BrevTilbakekrevingsbehandlingService,
    forhåndsvarsleTilbakekrevingsbehandlingService: ForhåndsvarsleTilbakekrevingsbehandlingService,
    forhåndsvisForhåndsvarselTilbakekrevingsbehandlingService: ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService,
    tilbakekrevingsbehandlingTilAttesteringService: TilbakekrevingsbehandlingTilAttesteringService,
    iverksettTilbakekrevingService: IverksettTilbakekrevingService,
) {
    this.opprettTilbakekrevingsbehandlingRoute(opprettTilbakekrevingsbehandlingService)
    this.månedsvurderingerTilbakekrevingsbehandlingRoute(månedsvurderingerTilbakekrevingsbehandlingService)
    this.brevTilbakekrevingsbehandlingRoute(brevTilbakekrevingsbehandlingService)
    this.forhåndsvarsleTilbakekrevingRoute(forhåndsvarsleTilbakekrevingsbehandlingService)
    this.visForhåndsvarselTilbakekrevingsbrev(forhåndsvisForhåndsvarselTilbakekrevingsbehandlingService)
    this.tilAttesteringTilbakekrevingsbehandlingRoute(tilbakekrevingsbehandlingTilAttesteringService)
    this.iverksettTilbakekrevingsbehandlingRoute(iverksettTilbakekrevingService)
}
