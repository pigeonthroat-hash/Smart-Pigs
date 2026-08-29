package com.smartpigs.overlay

import android.content.Context
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("smart_pigs", Context.MODE_PRIVATE)

    fun getJson(): JSONObject {
        val raw = prefs.getString("settings", null)
        return if (raw.isNullOrBlank()) {
            val mods = JSONObject()
            PigEngine.defaultModifiers().forEach { (k, v) -> mods.put(k, v) }
            JSONObject()
                .put("pigCount", 2)
                .put("pigWidth", 168)
                .put("imageUrl", "https://static.wikia.nocookie.net/neutral-characters/images/d/d0/PeppaPig.webp/revision/latest?cb=20250701022738")
                .put("modifiers", mods)
        } else JSONObject(raw)
    }

    fun putJson(obj: JSONObject) {
        prefs.edit().putString("settings", obj.toString()).apply()
    }

    fun merge(partial: JSONObject) {
        val cur = getJson()
        val keys = partial.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            cur.put(k, partial.get(k))
        }
        putJson(cur)
    }
}
