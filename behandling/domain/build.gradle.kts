dependencies {
    implementation(project(":common:domain"))
    implementation(project(":satser"))
    implementation(project(":beregning"))

    implementation(project(":vilkår:vurderinger"))
    implementation(project(":vilkår:bosituasjon"))
    implementation(project(":vilkår:inntekt:domain"))
    implementation(project(":vilkår:formue:domain"))
    implementation(project(":vilkår:opplysningsplikt:domain"))
    implementation(project(":vilkår:skatt:domain"))
    implementation(project(":økonomi:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("behandling-domain")
}
