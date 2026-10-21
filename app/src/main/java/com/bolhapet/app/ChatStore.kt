package com.bolhapet.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class ChatStore(context: Context) {
    private val prefs = context.getSharedPreferences("bolha_chat_v1", Context.MODE_PRIVATE)
    private val history = mutableListOf<Pair<String, String>>()

    init {
        runCatching {
            val array = JSONArray(prefs.getString("messages", "[]"))
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val role = item.optString("role")
                val text = item.optString("text")
                if (role in setOf("user", "pet") && text.isNotBlank()) history += role to text
            }
        }
    }

    fun list(): JSONArray = JSONArray().also { array ->
        history.takeLast(36).forEach { (role, text) ->
            array.put(JSONObject().put("role", role).put("text", text))
        }
    }

    fun remember(role: String, text: String) {
        require(role == "user" || role == "pet")
        if (text.isBlank()) return
        history += role to text.take(3800)
        while (history.size > 36) history.removeAt(0)
        prefs.edit().putString("messages", list().toString()).apply()
    }

    fun recentContext(): String = buildString {
        history.takeLast(8).forEach { (role, message) ->
            append(if (role == "user") "Pessoa: " else "Bolha: ")
            appendLine(message.take(550).replace("[[emocao:", "[emoção antiga:"))
        }
    }.takeLast(3900)
}
