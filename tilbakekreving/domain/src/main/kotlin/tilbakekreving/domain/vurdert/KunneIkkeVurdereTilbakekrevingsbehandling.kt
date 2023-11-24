package tilbakekreving.domain.vurdert

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeVurdereTilbakekrevingsbehandling {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeVurdereTilbakekrevingsbehandling
    data object UlikVersjon : KunneIkkeVurdereTilbakekrevingsbehandling

    /**
     * Dersom det har kommet nye endringer på kravgrunnlaget siden behandlingen ble vurdert.
     * Saksbehandler må da oppdatere kravgrunnlaget.
     */
    data object KravgrunnlagetHarEndretSeg : KunneIkkeVurdereTilbakekrevingsbehandling
}
