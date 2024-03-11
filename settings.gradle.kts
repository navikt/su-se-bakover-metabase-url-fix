pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "su-se-bakover"

include("application")
include("behandling:domain")
include("behandling:klage:domain")
include("behandling:regulering:domain")
include("behandling:revurdering:domain")
include("behandling:revurdering:presentation")
include("behandling:søknadsbehandling:domain")
include("behandling:søknadsbehandling:presentation")
include("beregning")
include("client")
include("common:domain")
include("common:infrastructure")
include("common:infrastructure:cxf")
include("common:presentation")
include("database")
include("datapakker:fritekstAvslag")
include("datapakker:soknad")
include("dokument:application")
include("dokument:domain")
include("dokument:infrastructure")
include("dokument:presentation")
include("domain")
include("grunnbeløp")
include("hendelse:domain")
include("hendelse:infrastructure")
include("kontrollsamtale:application")
include("kontrollsamtale:domain")
include("kontrollsamtale:infrastructure")
include("nøkkeltall:application")
include("nøkkeltall:domain")
include("nøkkeltall:infrastructure")
include("nøkkeltall:presentation")
include("oppgave:application")
include("oppgave:domain")
include("oppgave:infrastructure")
include("person:application")
include("person:domain")
include("person:infrastructure")
include("satser")
include("service")
include("statistikk")
include("test-common")
include("tilbakekreving:application")
include("tilbakekreving:domain")
include("tilbakekreving:infrastructure")
include("tilbakekreving:presentation")
include("vedtak:application")
include("vedtak:domain")
include("vilkår:bosituasjon:domain")
include("vilkår:common")
include("vilkår:familiegjenforening:domain")
include("vilkår:fastopphold:domain")
include("vilkår:flyktning:domain")
include("vilkår:formue:domain")
include("vilkår:inntekt:domain")
include("vilkår:institusjonsopphold:application")
include("vilkår:institusjonsopphold:domain")
include("vilkår:institusjonsopphold:infrastructure")
include("vilkår:institusjonsopphold:presentation")
include("vilkår:lovligopphold:domain")
include("vilkår:opplysningsplikt:domain")
include("vilkår:pensjon:domain")
include("vilkår:personligoppmøte:domain")
include("vilkår:skatt:application")
include("vilkår:skatt:domain")
include("vilkår:skatt:infrastructure")
include("vilkår:uføre:domain")
include("vilkår:utenlandsopphold:application")
include("vilkår:utenlandsopphold:domain")
include("vilkår:utenlandsopphold:infrastructure")
include("vilkår:vurderinger:domain")
include("vilkår:vurderinger:presentation")
include("web")
include("web-regresjonstest")
include("økonomi:application")
include("økonomi:domain")
include("økonomi:infrastructure")
include("økonomi:presentation")
