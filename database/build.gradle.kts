dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":utenlandsopphold:domain"))
    implementation(project(":utenlandsopphold:infrastructure"))
    implementation(project(":økonomi:domain"))
    implementation(project(":dokument:domain"))

    implementation(project(":institusjonsopphold:infrastructure"))
    implementation(project(":institusjonsopphold:domain"))

    implementation(project(":oppgave:infrastructure"))
    implementation(project(":oppgave:domain"))

    implementation(project(":tilbakekreving:domain"))

    testImplementation(project(":test-common"))
}
