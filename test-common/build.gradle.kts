// Contains shared test-data, functions and extension funcions to be used across modules
dependencies {
    val kotestVersion = "5.8.0"

    api(project(":behandling:domain"))
    api(project(":beregning"))
    api(project(":client"))
    api(project(":common:domain"))
    api(project(":common:infrastructure"))
    api(project(":database"))
    api(project(":dokument:domain"))
    api(project(":dokument:infrastructure"))
    api(project(":domain"))
    api(project(":grunnbeløp"))
    api(project(":hendelse:domain"))
    api(project(":hendelse:infrastructure"))
    api(project(":kontrollsamtale:application"))
    api(project(":kontrollsamtale:domain"))
    api(project(":kontrollsamtale:infrastructure"))
    api(project(":oppgave:domain"))
    api(project(":person:domain"))
    api(project(":satser"))
    api(project(":service"))
    api(project(":tilbakekreving:application"))
    api(project(":tilbakekreving:domain"))
    api(project(":tilbakekreving:infrastructure"))
    api(project(":tilbakekreving:presentation"))
    api(project(":vedtak:application"))
    api(project(":vedtak:domain"))
    api(project(":vedtak:domain"))
    api(project(":vilkår:common"))
    api(project(":vilkår:fastopphold:domain"))
    api(project(":vilkår:formue:domain"))
    api(project(":vilkår:institusjonsopphold:domain"))
    api(project(":vilkår:institusjonsopphold:presentation"))
    api(project(":vilkår:uføre:domain"))
    api(project(":vilkår:flyktning:domain"))
    api(project(":vilkår:lovligopphold:domain"))
    api(project(":vilkår:utenlandsopphold:domain"))
    api(project(":vilkår:pensjon:domain"))
    api(project(":vilkår:inntekt:domain"))
    api(project(":vilkår:opplysningsplikt:domain"))
    api(project(":vilkår:bosituasjon"))
    api(project(":web"))
    api(project(":økonomi:application"))
    api(project(":økonomi:domain"))

    compileOnly("io.kotest:kotest-assertions-core:$kotestVersion")
    // TODO jah: Kan gjenbruke versjoner ved å bruke gradle/libs.versions.toml
    compileOnly("org.mockito.kotlin:mockito-kotlin:5.2.1")
    compileOnly("org.skyscreamer:jsonassert:1.5.1")
    compileOnly("io.zonky.test:embedded-postgres:2.0.6")
    compileOnly(rootProject.libs.jupiter.api)
    api(rootProject.libs.wiremock) {
        exclude(group = "junit")
    }
}
