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

tasks.jar {
    manifest { 
        attributes["Main-Class"] = application.mainClass
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
