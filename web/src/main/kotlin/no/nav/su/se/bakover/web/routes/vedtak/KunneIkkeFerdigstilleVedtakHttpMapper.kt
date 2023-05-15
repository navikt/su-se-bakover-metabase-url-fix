package no.nav.su.se.bakover.web.routes.vedtak

import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtak
import no.nav.su.se.bakover.web.routes.sak.tilResultat

fun KunneIkkeFerdigstilleVedtak.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeFerdigstilleVedtak.KunneIkkeGenerereBrev -> this.underliggende.tilResultat()
        is KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave -> Feilresponser.kunneIkkeOppretteOppgave
    }
}
