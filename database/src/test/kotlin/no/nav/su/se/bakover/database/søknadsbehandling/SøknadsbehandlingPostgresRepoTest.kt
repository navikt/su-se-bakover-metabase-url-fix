package no.nav.su.se.bakover.database.søknadsbehandling

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.antall
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.dbMetricsStub
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.iverksattAttestering
import no.nav.su.se.bakover.database.saksbehandler
import no.nav.su.se.bakover.database.sessionCounterStub
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.enUkeEtterFixedTidspunkt
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulerNyUtbetaling
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkår.innvilgetFormueVilkår
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class SøknadsbehandlingPostgresRepoTest {

    @Test
    fun `hent for sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            testDataHelper.persisterSøknadsbehandlingBeregnetAvslag().second.also {
                testDataHelper.sessionFactory.withSessionContext { session ->
                    repo.hentForSak(it.sakId, session)
                }
            }
        }
    }

    @Test
    fun `kan sette inn tom saksbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val vilkårsvurdert = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Uavklart>()
            }
        }
    }

    @Test
    fun `lagring av vilkårsvurdert behandling påvirker ikke andre behandlinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning()
            testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget()

            dataSource.withSession { session ->
                "select count(1) from behandling where status = :status ".let {
                    it.antall(
                        mapOf("status" to BehandlingsStatus.VILKÅRSVURDERT_INNVILGET.toString()),
                        session,
                    ) shouldBe 1
                    it.antall(mapOf("status" to BehandlingsStatus.IVERKSATT_AVSLAG.toString()), session) shouldBe 1
                }
            }
        }
    }

    @Test
    fun `kan oppdatere med alle vilkår oppfylt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val vilkårsvurdert: Søknadsbehandling.Vilkårsvurdert.Innvilget =
                testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget().second
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
            }
        }
    }

    @Test
    fun `kan oppdatere med vilkår som fører til avslag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val vilkårsvurdert = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertAvslag().second
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Avslag>()
            }
        }
    }

    @Test
    fun `kan oppdatere stønadsperiode`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val vilkårsvurdert = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart(
                stønadsperiode = Stønadsperiode.create(periode = januar(2021)),
            ).second
            repo.hent(vilkårsvurdert.id).also {
                it?.stønadsperiode shouldBe Stønadsperiode.create(periode = januar(2021))
            }

            repo.lagre(
                vilkårsvurdert.oppdaterStønadsperiode(
                    oppdatertStønadsperiode = stønadsperiode2021,
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(fixedLocalDate),
                ).getOrFail(),
            )

            repo.hent(vilkårsvurdert.id).also {
                it?.stønadsperiode shouldBe stønadsperiode2021
            }
        }
    }

    @Test
    fun `oppdaterer status og vilkår og sletter beregning og simulering hvis de eksisterer`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val (sak, innvilgetVilkårsvurdering) = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget()
                .also { (_, innvilget) ->
                    repo.hent(innvilget.id) shouldBe innvilget
                    dataSource.withSession { session ->
                        "select * from behandling where id = :id".hent(mapOf("id" to innvilget.id), session) { row ->
                            row.stringOrNull("beregning") shouldBe null
                            row.stringOrNull("simulering") shouldBe null
                        }
                    }
                }

            val beregnet = innvilgetVilkårsvurdering
                .beregn(
                    begrunnelse = null,
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).getOrFail()
                .also {
                    repo.lagre(it)
                    repo.hent(it.id) shouldBe it
                    dataSource.withSession { session ->
                        "select * from behandling where id = :id".hent(
                            mapOf("id" to innvilgetVilkårsvurdering.id),
                            session,
                        ) { row ->
                            row.stringOrNull("beregning") shouldNotBe null
                            row.stringOrNull("simulering") shouldBe null
                        }
                    }
                }

            val simulert = beregnet.simuler(
                saksbehandler = saksbehandler,
            ) {
                simulerNyUtbetaling(
                    sak = sak,
                    request = it,
                    clock = fixedClock,
                )
            }.getOrFail().also { simulert ->
                repo.lagre(simulert)
                repo.hent(simulert.id) shouldBe simulert
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(
                        mapOf("id" to innvilgetVilkårsvurdering.id),
                        it,
                    ) { row ->
                        row.stringOrNull("beregning") shouldNotBe null
                        row.stringOrNull("simulering") shouldNotBe null
                    }
                }
            }

            // Tilbake til vilkårsvurdert
            simulert.leggTilFormuevilkår(
                vilkår = innvilgetFormueVilkår(),
            ).getOrFail().also { vilkårsvurdert ->
                repo.lagre(vilkårsvurdert)
                repo.hent(vilkårsvurdert.id) shouldBe vilkårsvurdert
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(
                        mapOf("id" to innvilgetVilkårsvurdering.id),
                        it,
                    ) { row ->
                        row.stringOrNull("beregning") shouldBe null
                        row.stringOrNull("simulering") shouldBe null
                    }
                }
            }
        }
    }

    @Nested
    inner class TilAttestering {
        @Test
        fun `til attestering innvilget`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val repo = testDataHelper.søknadsbehandlingRepo
                val tilAttestering = testDataHelper.persisterSøknadsbehandlingTilAttesteringInnvilget().second
                repo.hent(tilAttestering.id) shouldBe tilAttestering
            }
        }
    }

    @Test
    fun `til attestering avslag uten beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingTilAttesteringAvslagUtenBeregning().second
            repo.hent(tilAttestering.id) shouldBe tilAttestering
        }
    }

    @Test
    fun `til attestering avslag med beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingTilAttesteringAvslagMedBeregning().second
            repo.hent(tilAttestering.id) shouldBe tilAttestering
        }
    }

    @Nested
    inner class Underkjent {
        @Test
        fun `underkjent innvilget`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val repo = testDataHelper.søknadsbehandlingRepo
                val tilAttestering = testDataHelper.persisterSøknadsbehandlingUnderkjentInnvilget().second
                repo.hent(tilAttestering.id) shouldBe tilAttestering
            }
        }
    }

    @Test
    fun `underkjent avslag uten beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingUnderkjentAvslagUtenBeregning().second
            repo.hent(tilAttestering.id) shouldBe tilAttestering
        }
    }

    @Test
    fun `underkjent avslag med beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingUnderkjentAvslagMedBeregning().second
            repo.hent(tilAttestering.id) shouldBe tilAttestering
        }
    }

    @Nested
    inner class Iverksatt {
        @Test
        fun `iverksatt avslag innvilget`() {

            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val repo = testDataHelper.søknadsbehandlingRepo
                val iverksatt = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget().second
                repo.hent(iverksatt.id) shouldBe iverksatt
            }
        }
    }

    @Test
    fun `iverksatt avslag uten beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val iverksatt =
                testDataHelper.persisterSøknadsbehandlingIverksattAvslagUtenBeregning(fritekstTilBrev = "Dette er fritekst").second
            val expected = Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
                id = iverksatt.id,
                opprettet = iverksatt.opprettet,
                sakId = iverksatt.sakId,
                saksnummer = iverksatt.saksnummer,
                søknad = iverksatt.søknad,
                oppgaveId = iverksatt.oppgaveId,
                fnr = iverksatt.fnr,
                saksbehandler = saksbehandler,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(iverksattAttestering),
                fritekstTilBrev = "Dette er fritekst",
                stønadsperiode = stønadsperiode2021,
                grunnlagsdata = iverksatt.grunnlagsdata,
                vilkårsvurderinger = iverksatt.vilkårsvurderinger,
                avkorting = AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                    håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
                ),
                sakstype = iverksatt.sakstype,
            )
            repo.hent(iverksatt.id).also {
                it shouldBe expected
            }
        }
    }

    @Test
    fun `iverksatt avslag med beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val iverksatt = testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning().second
            repo.hent(iverksatt.id) shouldBe iverksatt
        }
    }

    @Test
    fun `iverksatt avslag med beregning med grunnlag og vilkårsvurderinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val iverksatt = testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning().second
            repo.hent(iverksatt.id) shouldBe iverksatt
        }
    }

    @Test
    fun `søknad har ikke påbegynt behandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandlingRepo = testDataHelper.søknadsbehandlingRepo
            val nySak: NySak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            søknadsbehandlingRepo.hentForSøknad(nySak.søknad.id) shouldBe null
        }
    }

    @Test
    fun `søknad har påbegynt behandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandlingRepo = testDataHelper.søknadsbehandlingRepo
            testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second.let {
                søknadsbehandlingRepo.hentForSøknad(it.søknad.id) shouldBe it
            }
        }
    }

    @Test
    fun `kan lagre og hente avslag manglende dokumentasjon`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val opprettetMedStønadsperiode =
                testDataHelper.persisterSøknadsbehandlingIverksattAvslagUtenBeregning().second

            testDataHelper.søknadsbehandlingRepo.lagreAvslagManglendeDokumentasjon(
                avslag = AvslagManglendeDokumentasjon(søknadsbehandling = opprettetMedStønadsperiode),
            )

            testDataHelper.søknadsbehandlingRepo.hent(opprettetMedStønadsperiode.id) shouldBe Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
                id = opprettetMedStønadsperiode.id,
                opprettet = opprettetMedStønadsperiode.opprettet,
                sakId = opprettetMedStønadsperiode.sakId,
                saksnummer = opprettetMedStønadsperiode.saksnummer,
                søknad = opprettetMedStønadsperiode.søknad,
                oppgaveId = opprettetMedStønadsperiode.oppgaveId,
                fnr = opprettetMedStønadsperiode.fnr,
                saksbehandler = saksbehandler,
                attesteringer = Attesteringshistorikk.create(
                    attesteringer = listOf(
                        Attestering.Iverksatt(
                            attestant = NavIdentBruker.Attestant(attestant.navIdent),
                            opprettet = enUkeEtterFixedTidspunkt,
                        ),
                    ),
                ),
                fritekstTilBrev = "",
                stønadsperiode = opprettetMedStønadsperiode.stønadsperiode,
                grunnlagsdata = opprettetMedStønadsperiode.grunnlagsdata,
                vilkårsvurderinger = opprettetMedStønadsperiode.vilkårsvurderinger,
                avkorting = AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                    håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
                ),
                sakstype = opprettetMedStønadsperiode.sakstype,
            )
        }
    }

    @Test
    fun `gjør ingenting med avkorting dersom ingenting har blitt avkortet`() {
        withMigratedDb { dataSource ->
            val iverksattInnvilgetUtenAvkorting = søknadsbehandlingIverksattInnvilget().second
            val iverksattAvslagMedBeregning = søknadsbehandlingIverksattAvslagMedBeregning().second
            val iverksattAvslagUtenBeregning = søknadsbehandlingIverksattAvslagUtenBeregning().second

            val avkortingsvarselRepoMock = mock<AvkortingsvarselPostgresRepo>()

            val sessionFactory = PostgresSessionFactory(dataSource, dbMetricsStub, sessionCounterStub)

            val repo = SøknadsbehandlingPostgresRepo(
                dbMetrics = dbMetricsStub,
                sessionFactory = PostgresSessionFactory(dataSource, dbMetricsStub, sessionCounterStub),
                avkortingsvarselRepo = avkortingsvarselRepoMock,
                grunnlagsdataOgVilkårsvurderingerPostgresRepo = mock(),
                satsFactory = satsFactoryTest,
            )

            repo.lagre(
                søknadsbehandling = iverksattInnvilgetUtenAvkorting,
                sessionContext = sessionFactory.newTransactionContext(),
            )
            iverksattInnvilgetUtenAvkorting.avkorting shouldBe AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående

            repo.lagre(
                søknadsbehandling = iverksattAvslagMedBeregning,
                sessionContext = sessionFactory.newTransactionContext(),
            )
            iverksattAvslagMedBeregning.avkorting shouldBe AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
            )

            repo.lagre(
                søknadsbehandling = iverksattAvslagUtenBeregning,
                sessionContext = sessionFactory.newTransactionContext(),
            )
            iverksattAvslagUtenBeregning.avkorting shouldBe AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
            )

            verifyNoInteractions(avkortingsvarselRepoMock)
        }
    }

    @Test
    fun `oppdaterer avkorting ved lagring av iverksatt innvilget søknadsbehandling med avkorting`() {
        withMigratedDb { dataSource ->
            val avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                    objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        sakId = UUID.randomUUID(),
                        revurderingId = UUID.randomUUID(),
                        simulering = simuleringFeilutbetaling(juni(2021)),
                    ),
                ),
            ).håndter().iverksett(UUID.randomUUID())

            val iverksattInnvilgetAvkortet = søknadsbehandlingIverksattInnvilget().let { (_, iverksatt) ->
                iverksatt.copy(avkorting = avkorting)
            }

            val avkortingsvarselRepoMock = mock<AvkortingsvarselPostgresRepo>()

            val sessionFactory = PostgresSessionFactory(dataSource, dbMetricsStub, sessionCounterStub)

            val repo = SøknadsbehandlingPostgresRepo(
                dbMetrics = dbMetricsStub,
                sessionFactory = sessionFactory,
                avkortingsvarselRepo = avkortingsvarselRepoMock,
                grunnlagsdataOgVilkårsvurderingerPostgresRepo = mock(),
                satsFactory = satsFactoryTest,
            )

            repo.lagre(
                søknadsbehandling = iverksattInnvilgetAvkortet,
                sessionContext = sessionFactory.newTransactionContext(),
            )

            verify(avkortingsvarselRepoMock).lagre(
                avkortingsvarsel = argThat<Avkortingsvarsel.Utenlandsopphold.Avkortet> { it shouldBe avkorting.avkortingsvarsel },
                tx = anyOrNull(),
            )
            verifyNoMoreInteractions(avkortingsvarselRepoMock)
        }
    }
}
