package me.kafuuneko.rpclient.libs.regex

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexScriptCodecTest {
    private val codec = RegexScriptCodec(Gson())

    @Test
    fun sillyTavernScriptRoundTripsKnownAndUnknownFields() {
        val json = """
            [{
              "id":"one",
              "scriptName":"Test",
              "findRegex":"/foo/gi",
              "replaceString":"bar",
              "trimStrings":["x"],
              "placement":[1,2],
              "disabled":false,
              "markdownOnly":true,
              "promptOnly":false,
              "runOnEdit":true,
              "substituteRegex":2,
              "minDepth":1,
              "maxDepth":4,
              "custom":{"kept":true}
            }]
        """.trimIndent()

        val output = JsonParser.parseString(codec.toJson(codec.parseList(json))).asJsonArray[0].asJsonObject

        assertEquals("one", output["id"].asString)
        assertEquals(2, output["substituteRegex"].asInt)
        assertTrue(output["custom"].asJsonObject["kept"].asBoolean)
    }
}
