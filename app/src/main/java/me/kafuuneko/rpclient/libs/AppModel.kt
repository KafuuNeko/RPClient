package me.kafuuneko.rpclient.libs

import com.chibatching.kotpref.KotprefModel

object AppModel : KotprefModel() {
    const val EMAIL = "kafuuneko@gmail.com"

    var currentLLMProvider by longPref()

}