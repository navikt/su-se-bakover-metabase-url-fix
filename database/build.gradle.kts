dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    implementation(project(":domain"))

    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))

    implementation(project(":økonomi:domain"))

    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))

    implementation(project(":vilkår:utenlandsopphold:domain"))
    implementation(project(":vilkår:utenlandsopphold:infrastructure"))
    implementation(project(":vilkår:institusjonsopphold:infrastructure"))
    implementation(project(":vilkår:institusjonsopphold:domain"))
    implementation(project(":vilkår:domain"))
    implementation(project(":vilkår:formue:domain"))

    implementation(project(":oppgave:infrastructure"))
    implementation(project(":oppgave:domain"))

    implementation(project(":person:domain"))

    implementation(project(":tilbakekreving:domain"))
    implementation(project(":tilbakekreving:infrastructure"))

    implementation(project(":behandling:domain"))

    implementation(project(":sats"))

    testImplementation(project(":test-common"))
}
