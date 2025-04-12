plugins {
    alias(libs.plugins.kotlin)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    explicitApi()

    compilerOptions { 
        optIn.addAll(
            "kotlin.ExperimentalStdlibApi",
        )
    }
}

dependencies {
    implementation(libs.bundles.scripting.client)
}
