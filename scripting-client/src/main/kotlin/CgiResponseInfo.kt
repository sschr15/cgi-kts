package com.sschr15.scripting

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
public class CgiResponseInfo(public val envRequest: (String) -> String?) {
    public lateinit var result: String
}
