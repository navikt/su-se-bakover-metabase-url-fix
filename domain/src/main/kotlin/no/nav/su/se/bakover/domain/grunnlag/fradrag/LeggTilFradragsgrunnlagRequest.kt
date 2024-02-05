package no.nav.su.se.bakover.domain.grunnlag.fradrag

import no.nav.su.se.bakover.common.domain.BehandlingsId
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag

data class LeggTilFradragsgrunnlagRequest(
    val behandlingId: BehandlingsId,
    val fradragsgrunnlag: List<Fradragsgrunnlag>,
)
