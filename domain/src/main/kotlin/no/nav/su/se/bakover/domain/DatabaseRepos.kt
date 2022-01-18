package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.grunnlag.FormueVilkårsvurderingRepo
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagRepo
import no.nav.su.se.bakover.domain.grunnlag.UføreVilkårsvurderingRepo
import no.nav.su.se.bakover.domain.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlagevedtakRepo
import no.nav.su.se.bakover.domain.nøkkeltall.NøkkeltallRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.person.PersonRepo
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo

data class DatabaseRepos(
    val avstemming: AvstemmingRepo,
    val utbetaling: UtbetalingRepo,
    val søknad: SøknadRepo,
    val hendelseslogg: HendelsesloggRepo,
    val sak: SakRepo,
    val person: PersonRepo,
    val søknadsbehandling: SøknadsbehandlingRepo,
    val revurderingRepo: RevurderingRepo,
    val vedtakRepo: VedtakRepo,
    val grunnlagRepo: GrunnlagRepo,
    val uføreVilkårsvurderingRepo: UføreVilkårsvurderingRepo,
    val formueVilkårsvurderingRepo: FormueVilkårsvurderingRepo,
    val personhendelseRepo: PersonhendelseRepo,
    val dokumentRepo: DokumentRepo,
    val nøkkeltallRepo: NøkkeltallRepo,
    val sessionFactory: SessionFactory,
    val klageRepo: KlageRepo,
    val klageVedtakRepo: KlagevedtakRepo,
)