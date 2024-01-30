dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))
    implementation(project(":client"))
    implementation(project(":statistikk"))
    implementation(project(":økonomi:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))
    implementation(project(":oppgave:domain"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":vedtak:domain"))
    implementation(project(":vedtak:application"))
    implementation(project(":person:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":satser"))
    implementation(project(":vilkår:common"))
    implementation(project(":vilkår:formue:domain"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":vilkår:flyktning:domain"))
    implementation(project(":vilkår:fastopphold:domain"))
    implementation(project(":vilkår:lovligopphold:domain"))
    implementation(project(":vilkår:institusjonsopphold:domain"))
    implementation(project(":vilkår:utenlandsopphold:domain"))
    implementation(project(":vilkår:pensjon:domain"))
    implementation(project(":vilkår:inntekt:domain"))
    implementation(project(":vilkår:opplysningsplikt:domain"))
    implementation(project(":vilkår:personligoppmøte:domain"))
    implementation(project(":vilkår:familiegjenforening:domain"))
    implementation(project(":vilkår:bosituasjon"))
    implementation(project(":vilkår:vurderinger"))
    implementation(project(":vilkår:skatt:domain"))
    implementation(project(":beregning"))

    testImplementation(project(":vilkår:utenlandsopphold:domain"))
    testImplementation(project(":test-common"))
}
