package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import java.util.UUID

interface Beregning : PeriodisertInformasjon {
    fun getId(): UUID
    fun getOpprettet(): Tidspunkt
    fun getSats(): Sats
    fun getMånedsberegninger(): List<Månedsberegning>
    fun getFradrag(): List<Fradrag>
    fun getSumYtelse(): Int
    fun getSumFradrag(): Double
    fun getSumYtelseErUnderMinstebeløp(): Boolean
    fun getFradragStrategyName(): FradragStrategyName
    fun utledAvslagsgrunner(): List<Avslagsgrunn>
}
