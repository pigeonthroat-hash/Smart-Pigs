package com.smartpigs.overlay

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PigsJsBridge(
    private val service: OverlayService,
    private val store: SettingsStore,
) {
    private val main = Handler(Looper.getMainLooper())

    private fun onMain(block: () -> JSONObject): JSONObject {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return try {
                block()
            } catch (e: Exception) {
                JSONObject().put("success", false).put("message", e.message ?: "error")
            }
        }
        var result = JSONObject().put("success", false).put("message", "timeout")
        val latch = CountDownLatch(1)
        main.post {
            result = try {
                block()
            } catch (e: Exception) {
                JSONObject().put("success", false).put("message", e.message ?: "error")
            } finally {
                latch.countDown()
                result
            }
        }
        latch.await(2, TimeUnit.SECONDS)
        return result
    }

    @JavascriptInterface
    fun send(json: String): String {
        val extra = JSONObject(json)
        val action = extra.optString("action")
        return onMain { service.handleAction(action, extra) }.toString()
    }

    @JavascriptInterface
    fun storageGet(keysJson: String): String {
        val stored = store.getJson()
        return try {
            val keys = JSONArray(keysJson)
            val out = JSONObject()
            for (i in 0 until keys.length()) {
                val k = keys.getString(i)
                if (stored.has(k)) out.put(k, stored.get(k))
            }
            out.toString()
        } catch (_: Exception) {
            stored.toString()
        }
    }

    @JavascriptInterface
    fun storageSet(objJson: String): String {
        store.merge(JSONObject(objJson))
        return "{\"success\":true}"
    }

    @JavascriptInterface
    fun closePopup() {
        main.post { service.setPopupOpen(false) }
    }
}