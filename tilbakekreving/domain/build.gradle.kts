dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":person:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":oppgave:domain"))
    implementation(project(":tilgangstyring:domain"))
    implementation(project(":vedtak:domain"))
    implementation(project(":behandling:common:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("tilbakekreving-domain")
}

tasks.test {
    useJUnitPlatform()
}
