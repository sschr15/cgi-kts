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
}

dependencies {
    implementation(libs.bundles.scripting.client)
}
