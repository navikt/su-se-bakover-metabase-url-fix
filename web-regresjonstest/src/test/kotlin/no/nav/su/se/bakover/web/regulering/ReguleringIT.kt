package no.nav.su.se.bakover.web.regulering

import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.domain.tid.fixedClock
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.komponenttest.testSusebakover
import no.nav.su.se.bakover.web.sak.hent.hentReguleringMedId
import no.nav.su.se.bakover.web.sak.hent.hentReguleringer
import no.nav.su.se.bakover.web.sak.hent.hentSakForFnr
import no.nav.su.se.bakover.web.sak.hent.hentSakId
import no.nav.su.se.bakover.web.søknadsbehandling.GrunnlagJson
import no.nav.su.se.bakover.web.søknadsbehandling.ReguleringJson
import no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon.bosituasjonEpsJson
import no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon.leggTilBosituasjon
import no.nav.su.se.bakover.web.søknadsbehandling.formue.formueEpsJson
import no.nav.su.se.bakover.web.søknadsbehandling.formue.leggTilFormue
import no.nav.su.se.bakover.web.søknadsbehandling.fradrag.leggTilFradrag
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test

internal class ReguleringIT {

    @Test
    fun `automatisk regulering - uten supplement`() {
        val fnrForSakSomSkalReguleres = Fnr.generer().toString()
        withMigratedDb { dataSource ->
            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = fixedClock,
                    applicationConfig = applicationConfig(),
                )
                application { testSusebakover(appComponents = appComponents) }
                opprettInnvilgetSøknadsbehandling(
                    fnr = fnrForSakSomSkalReguleres,
                    client = this.client,
                    appComponents = appComponents,
                )
            }

            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = fixedClockAt(21.mai(2021)),
                    applicationConfig = applicationConfig(),
                )
                application { testSusebakover(appComponents = appComponents) }
                val fnrForSakSomIkkeSkalBliRegulert = Fnr.generer().toString()
                opprettInnvilgetSøknadsbehandling(
                    fnr = fnrForSakSomIkkeSkalBliRegulert,
                    client = this.client,
                    appComponents = appComponents,
                )
                regulerAutomatisk(mai(2021), this.client)

                val sakMedRegulering = hentSakForFnr(fnrForSakSomSkalReguleres, client = this.client)
                val sakUtenRegulering = hentSakForFnr(fnrForSakSomIkkeSkalBliRegulert, client = this.client)
                hentReguleringer(sakUtenRegulering) shouldBe "[]"
                val reguleringen = ReguleringJson.hentSingleReglering(hentReguleringer(sakMedRegulering))
                verifyIverksattReguleringFraAutomatisk(reguleringen, fnrForSakSomSkalReguleres)
            }
        }
    }

    @Test
    fun `manuell regulering`() {
        val fnrForSakSomSkalReguleres = Fnr.generer().toString()
        withMigratedDb { dataSource ->
            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = fixedClock,
                    applicationConfig = applicationConfig(),
                )
                application { testSusebakover(appComponents = appComponents) }
                opprettInnvilgetSøknadsbehandling(
                    fnr = fnrForSakSomSkalReguleres,
                    client = this.client,
                    appComponents = appComponents,
                    fradrag = { sakId, behandlingId ->
                        leggTilFradrag(
                            sakId = sakId,
                            behandlingId = behandlingId,
                            client = this.client,
                            body = {
                                //language=json
                                """{"fradrag": [{"periode": {"fraOgMed": "2021-01-01","tilOgMed": "2021-12-31"},"type": "Alderspensjon","beløp": 10000.0,"utenlandskInntekt": null,"tilhører": "BRUKER"}]}""".trimIndent()
                            },
                        )
                    },
                )
            }
            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = 21.mai(2021).fixedClock(),
                    applicationConfig = applicationConfig(),
                )
                application { testSusebakover(appComponents = appComponents) }
                regulerAutomatisk(mai(2021), this.client)
                val sak = hentSakForFnr(fnrForSakSomSkalReguleres, client = this.client)
                val sakId = hentSakId(sak)
                val reguleringen = ReguleringJson.hentSingleReglering(hentReguleringer(sak))
                val reguleringsId = ReguleringJson.id(reguleringen)
                val uføregrunnlag = ReguleringJson.hentSingleUføregrunnlag(reguleringen)
                val uføregrunnlagId = GrunnlagJson.id(uføregrunnlag)
                val uføregrunnlagOpprettet = GrunnlagJson.opprettet(uføregrunnlag)

                manuellRegulering(
                    reguleringsId = reguleringsId,
                    //language=json
                    oppdatertUføre = """[{"forventetInntekt":25,"opprettet":"$uføregrunnlagOpprettet","uføregrad":100,"id":"$uføregrunnlagId","periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}]""".trimIndent(),
                    //language=json
                    oppdatertFradrag = """[{"beløp":10050,"tilhører":"BRUKER","utenlandskInntekt":null,"type":"Alderspensjon","beskrivelse":null,"periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}}]""".trimIndent(),
                    client = this.client,
                )
                val iverksattReg =
                    hentSakForFnr(fnrForSakSomSkalReguleres, client = this.client).hentReguleringMedId(reguleringsId)
                verifyRegulering(iverksattReg, reguleringsId, sakId, fnrForSakSomSkalReguleres)
            }
        }
    }

    @Test
    fun `automatisk regulering med supplement - 1 går automatisk, 1 går til manuell`() {
        val fnrForSakSomSkalReguleresGjennomSupplement = Fnr.generer().toString()
        val fnrForSakSomIkkeSkalReguleresPgaInntektsendring = Fnr.generer().toString()
        withMigratedDb { dataSource ->
            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = fixedClock,
                    applicationConfig = applicationConfig(),
                )
                application { testSusebakover(appComponents = appComponents) }
                opprettInnvilgetSøknadsbehandling(
                    fnr = fnrForSakSomSkalReguleresGjennomSupplement,
                    client = this.client,
                    appComponents = appComponents,
                    fradrag = { sakId, behandlingId ->
                        leggTilFradrag(
                            sakId = sakId,
                            behandlingId = behandlingId,
                            client = this.client,
                            body = {
                                //language=json
                                """{"fradrag": [{"periode": {"fraOgMed": "2021-01-01","tilOgMed": "2021-12-31"},"type": "Uføretrygd","beløp": 10000.0,"utenlandskInntekt": null,"tilhører": "BRUKER"}]}""".trimIndent()
                            },
                        )
                    },
                )

                opprettInnvilgetSøknadsbehandling(
                    fnr = fnrForSakSomIkkeSkalReguleresPgaInntektsendring,
                    client = this.client,
                    appComponents = appComponents,
                    fradrag = { sakId, behandlingId ->
                        leggTilFradrag(
                            sakId = sakId,
                            behandlingId = behandlingId,
                            client = this.client,
                            body = {
                                //language=json
                                """{"fradrag": [{"periode": {"fraOgMed": "2021-01-01","tilOgMed": "2021-12-31"},"type": "Alderspensjon","beløp": 10000.0,"utenlandskInntekt": null,"tilhører": "BRUKER"}]}""".trimIndent()
                            },
                        )
                    },
                )
            }

            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = fixedClockAt(21.mai(2021)),
                    applicationConfig = applicationConfig(),
                )
                application { testSusebakover(appComponents = appComponents) }
                regulerAutomatiskMultipart(
                    fraOgMed = mai(2021),
                    client = this.client,
                    supplement = """
                        FNR;K_SAK_T;K_VEDTAK_T;FOM_DATO;TOM_DATO;BRUTTO;NETTO;K_YTELSE_KOMP_T;BRUTTO_YK;NETTO_YK
                        $fnrForSakSomSkalReguleresGjennomSupplement;UFOREP;REGULERING;01.05.2021;;10500;10500;UT_ORDINER;10500;10500
                        $fnrForSakSomIkkeSkalReguleresPgaInntektsendring;ALDER;REGULERING;01.05.2021;;10900;10900;GP;10900;10900
                    """.trimIndent(),
                )
                val sakSomSkalHaBlittRegulertAutomatiskGjennomSupplement =
                    hentSakForFnr(fnrForSakSomSkalReguleresGjennomSupplement, client = this.client)
                val automatiskRegulert = ReguleringJson.hentSingleReglering(
                    hentReguleringer(sakSomSkalHaBlittRegulertAutomatiskGjennomSupplement),
                )
                val sakSomIkkeSkalHaBlittAutomatiskRegulertPgaInnteksendring =
                    hentSakForFnr(fnrForSakSomIkkeSkalReguleresPgaInntektsendring, client = this.client)
                val manuellRegulering = ReguleringJson.hentSingleReglering(
                    hentReguleringer(sakSomIkkeSkalHaBlittAutomatiskRegulertPgaInnteksendring),
                )

                verifyAutomatiskRegulertMedSupplement(automatiskRegulert, fnrForSakSomSkalReguleresGjennomSupplement)
                verifyManuellReguleringMedSupplement(manuellRegulering, fnrForSakSomIkkeSkalReguleresPgaInntektsendring)
            }
        }
    }

    @Test
    fun `regulerer automatisk med supplement for EPS - default request`() {
        val søkersFnr = Fnr.generer().toString()
        val epsFnr = Fnr.generer().toString()
        withMigratedDb { dataSource ->
            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = fixedClock,
                    applicationConfig = applicationConfig(),
                )
                application { testSusebakover(appComponents = appComponents) }

                opprettInnvilgetSøknadsbehandling(
                    fnr = søkersFnr,
                    client = this.client,
                    appComponents = appComponents,
                    leggTilBosituasjon = { sakId, behandlingId ->
                        leggTilBosituasjon(
                            sakId = sakId,
                            behandlingId = behandlingId,
                            body = { bosituasjonEpsJson(epsFnr) },
                            client = this.client,
                        )
                    },
                    leggTilFormue = { sakId, behandlingId ->
                        leggTilFormue(
                            sakId = sakId,
                            behandlingId = behandlingId,
                            body = { formueEpsJson() },
                            client = this.client,
                        )
                    },
                    fradrag = { sakId, behandlingId ->
                        leggTilFradrag(
                            sakId = sakId,
                            behandlingId = behandlingId,
                            client = this.client,
                            body = {
                                //language=json
                                """{"fradrag": [{"periode": {"fraOgMed": "2021-01-01","tilOgMed": "2021-12-31"},"type": "Uføretrygd","beløp": 10000.0,"utenlandskInntekt": null,"tilhører": "EPS"}]}""".trimIndent()
                            },
                        )
                    },
                )
            }

            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = fixedClockAt(21.mai(2021)),
                    applicationConfig = applicationConfig(),
                )
                application { testSusebakover(appComponents = appComponents) }
                val fraOgMed = mai(year = 2021)
                regulerAutomatisk(
                    fraOgMed = fraOgMed,
                    client = this.client,
                    //language=json
                    body = """{
                      "fraOgMedMåned": "$fraOgMed",
                      "csv": "FNR;K_SAK_T;K_VEDTAK_T;FOM_DATO;TOM_DATO;BRUTTO;NETTO;K_YTELSE_KOMP_T;BRUTTO_YK;NETTO_YK\r\n$epsFnr;UFOREP;REGULERING;01.05.2021;;10500;10500;UT_ORDINER;10500;10500"
                    }
                    """.trimIndent(),
                )
                val sak = hentSakForFnr(søkersFnr, client = this.client)
                val regulering = ReguleringJson.hentSingleReglering(hentReguleringer(sak))

                verifyAutomatiskRegulertForEPS(regulering, søkersFnr, epsFnr)
            }
        }
    }

    @Test
    fun `kan ettersende supplement`() {
        val fnrSomSkalBliAutomatiskRegulertNårSupplementBlirEttersendt = Fnr.generer().toString()
        withMigratedDb { dataSource ->
            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = fixedClock,
                    applicationConfig = applicationConfig(),
                )
                application { testSusebakover(appComponents = appComponents) }
                opprettInnvilgetSøknadsbehandling(
                    fnr = fnrSomSkalBliAutomatiskRegulertNårSupplementBlirEttersendt,
                    client = this.client,
                    appComponents = appComponents,
                    fradrag = { sakId, behandlingId ->
                        leggTilFradrag(
                            sakId = sakId,
                            behandlingId = behandlingId,
                            client = this.client,
                            body = {
                                //language=json
                                """{"fradrag": [{"periode": {"fraOgMed": "2021-01-01","tilOgMed": "2021-12-31"},"type": "Alderspensjon","beløp": 10000.0,"utenlandskInntekt": null,"tilhører": "BRUKER"}]}""".trimIndent()
                            },
                        )
                    },
                )
            }

            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = fixedClockAt(21.mai(2021)),
                    applicationConfig = applicationConfig(),
                )
                application { testSusebakover(appComponents = appComponents) }
                regulerAutomatisk(fraOgMed = mai(2021), client = this.client)
                val sakFørEttersendelse = hentSakForFnr(fnrSomSkalBliAutomatiskRegulertNårSupplementBlirEttersendt, client = this.client)
                val reguleringFørEttersendelse = ReguleringJson.hentSingleReglering(hentReguleringer(sakFørEttersendelse))
                verifyReguleringFørEttersendelse(reguleringFørEttersendelse, fnrSomSkalBliAutomatiskRegulertNårSupplementBlirEttersendt)

                ettersendSupplement(
                    fraOgMed = mai(2021),
                    supplement = """
                        FNR;K_SAK_T;K_VEDTAK_T;FOM_DATO;TOM_DATO;BRUTTO;NETTO;K_YTELSE_KOMP_T;BRUTTO_YK;NETTO_YK
                        $fnrSomSkalBliAutomatiskRegulertNårSupplementBlirEttersendt;ALDER;REGULERING;01.05.2021;;10500;10500;UT_ORDINER;10500;10500
                    """.trimIndent(),
                    client = this.client,
                )
                val sakEtterEttersendelse = hentSakForFnr(fnrSomSkalBliAutomatiskRegulertNårSupplementBlirEttersendt, client = this.client)
                val reguleringEtterEttersendelse = ReguleringJson.hentSingleReglering(hentReguleringer(sakEtterEttersendelse))
                verifyReguleringEtterEttersendelse(reguleringEtterEttersendelse, fnrSomSkalBliAutomatiskRegulertNårSupplementBlirEttersendt)
            }
        }
    }
}
