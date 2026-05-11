package me.kafuuneko.rpclient.libs.utils

fun String?.takeIfNotBlank(): String? {
    return this?.takeIf { it.isNotBlank() }
}