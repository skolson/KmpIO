package com.oldguy.common.io

import platform.Foundation.NSError

class NSErrorException(nsError: NSError): Exception(nsError.localizedDescription) {
    val code = nsError.code
    val domain = nsError.domain
    val description = nsError.description
    val reason = nsError.localizedFailureReason
    val suggestion = nsError.localizedRecoverySuggestion
    val causes = nsError.underlyingErrors.map { error ->
        val nsCause = error as NSError
        NSErrorException(nsCause)
    }

    override fun toString(): String {
        return buildString {
            append("Code: $code, domain: $domain, description: $description, reason: $reason\n")
            append("suggestion: $suggestion\n")
            causes.forEach {
                append("caused by\n")
                append(it.toString())
            }
        }
    }
}
