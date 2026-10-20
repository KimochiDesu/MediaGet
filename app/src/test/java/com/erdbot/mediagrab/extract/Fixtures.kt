package com.erdbot.mediagrab.extract

import org.json.JSONObject

object Fixtures {
    fun text(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing fixture $name" }.readText(Charsets.UTF_8)

    fun json(name: String) = JSONObject(text(name))
}
