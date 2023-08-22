tasks.named<Jar>("jar") {
    archiveBaseName.set("datapakker-soknad")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "no.nav.su.se.bakover.datapakker.soknad.AppKt"
        attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
            it.name
        }
    }
    doLast {
        configurations.runtimeClasspath.get().forEach {
            val file = File("$buildDir/libs/${it.name}")
            if (!file.exists()) {
                it.copyTo(file)
            }
        }
    }
}

dependencies {
    implementation(platform("com.google.cloud:libraries-bom:26.22.0"))
    implementation("com.google.cloud:google-cloud-bigquery")
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
}