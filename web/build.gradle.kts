plugins {
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

avro {
    isGettersReturnOptional.set(true)
    isOptionalGettersForNullableFieldsOnly.set(true)
}

dependencies {
    implementation(project(":behandling:domain"))
    implementation(project(":beregning"))
    implementation(project(":client"))
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":common:presentation"))
    implementation(project(":database"))
    implementation(project(":dokument:application"))
    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))
    implementation(project(":dokument:presentation"))
    implementation(project(":domain"))
    implementation(project(":grunnbeløp"))
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":kontrollsamtale:application"))
    implementation(project(":kontrollsamtale:domain"))
    implementation(project(":kontrollsamtale:infrastructure"))
    implementation(project(":oppgave:domain"))
    implementation(project(":oppgave:infrastructure"))
    implementation(project(":person:domain"))
    implementation(project(":satser"))
    implementation(project(":service"))
    implementation(project(":statistikk"))
    implementation(project(":tilbakekreving:application"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":tilbakekreving:infrastructure"))
    implementation(project(":tilbakekreving:presentation"))
    implementation(project(":vedtak:application"))
    implementation(project(":vedtak:domain"))
    implementation(project(":vilkår:common"))
    implementation(project(":vilkår:formue:domain"))
    implementation(project(":vilkår:institusjonsopphold:application"))
    implementation(project(":vilkår:institusjonsopphold:domain"))
    implementation(project(":vilkår:institusjonsopphold:infrastructure"))
    implementation(project(":vilkår:institusjonsopphold:presentation"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":vilkår:flyktning:domain"))
    implementation(project(":vilkår:fastopphold:domain"))
    implementation(project(":vilkår:lovligopphold:domain"))
    implementation(project(":vilkår:pensjon:domain"))
    implementation(project(":vilkår:inntekt:domain"))
    implementation(project(":vilkår:formue:domain"))
    implementation(project(":vilkår:opplysningsplikt:domain"))
    implementation(project(":vilkår:bosituasjon"))
    implementation(project(":vilkår:vurderinger"))
    implementation(project(":vilkår:utenlandsopphold:application"))
    implementation(project(":vilkår:utenlandsopphold:domain"))
    implementation(project(":vilkår:utenlandsopphold:infrastructure"))
    implementation(project(":økonomi:application"))
    implementation(project(":økonomi:domain"))
    implementation(project(":økonomi:infrastructure"))
    implementation(project(":økonomi:presentation"))

    testImplementation(project(":test-common"))
    testImplementation("org.awaitility:awaitility:4.2.0")
}

// Pluginen burde sette opp dette selv, men den virker discontinued.
tasks.named("compileKotlin").get().dependsOn(":web:generateAvroJava")
tasks.named("compileTestKotlin").get().dependsOn(":web:generateTestAvroJava")
