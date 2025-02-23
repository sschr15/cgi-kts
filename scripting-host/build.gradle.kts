plugins {
    alias(libs.plugins.kotlin)
    application
}

dependencies {
    implementation(libs.bundles.scripting.host)
    implementation(projects.scriptingClient)
}

java { 
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.sschr15.scripting.DaemonMain"
}
