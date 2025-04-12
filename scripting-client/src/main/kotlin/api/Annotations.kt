package com.sschr15.scripting.api

@MustBeDocumented
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This API may be unsafe to use."
)
public annotation class PossiblyUnsafeBehavior(
    val reason: String = ""
)

@Target(AnnotationTarget.FILE)
public annotation class DependsOnMavenCentral(
    public val coordinates: String = "",
    public val group: String = "",
    public val artifact: String = "",
    public val version: String = "",
    public val classifier: String = "",
    public val extension: String = "jar",
//        public val transitive: Boolean = true,
)

@Target(AnnotationTarget.FILE)
public annotation class DependsOnLocal(
    public val path: String,
)

@Target(AnnotationTarget.FILE)
public annotation class DependsOnUrl(
    public val url: String,
)
