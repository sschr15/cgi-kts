package com.sschr15.scripting

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
public class CgiResponseInfo(public val envRequest: (String) -> String?) {
    // If this were a Java class, this would not compile because of two functions called `getResult`.
    // However, in Kotlin, this is allowed as long as the return types are different.
    internal var oldResult: String
        @JvmName("getResult") get() = result.decodeToString()
        @JvmName("setResult") set(value) {
            result = value.encodeToByteArray()
        }

    public lateinit var result: ByteArray
}
