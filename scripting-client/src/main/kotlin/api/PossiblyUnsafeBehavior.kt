package com.sschr15.scripting.api

@MustBeDocumented
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This API may be unsafe to use."
)
public annotation class PossiblyUnsafeBehavior(
    val reason: String = ""
)
