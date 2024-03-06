dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":common:presentation"))
    implementation(project(":behandling:domain"))
    implementation(project(":behandling:søknadsbehandling:domain"))

    implementation(project(":vilkår:vurderinger"))
    implementation(project(":vilkår:common"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":vilkår:pensjon:domain"))
    implementation(project(":vilkår:flyktning:domain"))
    implementation(project(":vilkår:fastopphold:domain"))
    implementation(project(":vilkår:lovligopphold:domain"))
    implementation(project(":vilkår:institusjonsopphold:domain"))
    implementation(project(":vilkår:utenlandsopphold:domain"))
    implementation(project(":vilkår:inntekt:domain"))
    implementation(project(":vilkår:formue:domain"))
    implementation(project(":vilkår:personligoppmøte:domain"))
    implementation(project(":vilkår:opplysningsplikt:domain"))
    implementation(project(":vilkår:familiegjenforening:domain"))
    implementation(project(":vilkår:bosituasjon"))
    implementation(project(":vilkår:skatt:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("behandling-søknadsbehandling-presentation")
}
