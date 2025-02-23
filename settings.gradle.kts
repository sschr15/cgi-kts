plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  repositories { 
    mavenCentral()
  }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
  ":scripting-client",
  ":scripting-host",
)
