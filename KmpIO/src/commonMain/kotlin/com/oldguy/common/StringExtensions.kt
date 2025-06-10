package com.oldguy.common

fun Array<String>.containsIgnoreCase(s: String): Boolean {
    return any { it.equals(s, ignoreCase = true) }
}

fun List<String>.containsIgnoreCase(s: String): Boolean {
    return any { it.equals(s, ignoreCase = true) }
}