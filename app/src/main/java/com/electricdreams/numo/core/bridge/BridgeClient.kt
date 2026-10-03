package com.electricdreams.numo.core.bridge

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * HTTP client for the numo-bridge provider rail (Settings → Bridge).
 * Every call is synchronous — callers wrap in Dispatchers.IO.
 */
object BridgeClient {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun url(prefs: BridgePrefs, path: String): String =
        prefs.baseUrl.trimEnd('/') + path

    private fun call(request: Request): String {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) throw BridgeException(response.code, body)
            return body
        }
    }

    fun getMode(prefs: BridgePrefs): JSONObject =
        JSONObject(call(Request.Builder().url(url(prefs, "/mode")).build()))

    fun postMode(prefs: BridgePrefs, mode: String): JSONObject =
        JSONObject(
            call(
                Request.Builder().url(url(prefs, "/mode"))
                    .post(JSONObject().put("mode", mode).toString().toRequestBody(json))
                    .build()
            )
        )

    /** Returns the raw menu JSON: {mode, venue:{name,table}, items:[…]} */
    fun getMenu(prefs: BridgePrefs): JSONObject =
        JSONObject(call(Request.Builder().url(url(prefs, "/provider/menu")).build()))

    /** 202 {id, bolt11, amountSats, mode} */
    fun createOrder(prefs: BridgePrefs, items: List<Pair<String, Int>>): JSONObject {
        val arr = JSONArray()
        items.forEach { (sku, qty) ->
            arr.put(JSONObject().put("sku", sku).put("quantity", qty))
        }
        return JSONObject(
            call(
                Request.Builder().url(url(prefs, "/provider/orders"))
                    .post(JSONObject().put("items", arr).toString().toRequestBody(json))
                    .build()
            )
        )
    }

    fun pollOrder(prefs: BridgePrefs, id: String): JSONObject =
        JSONObject(call(Request.Builder().url(url(prefs, "/provider/orders/$id")).build()))

    fun submitOrder(prefs: BridgePrefs, id: String): JSONObject =
        JSONObject(
            call(
                Request.Builder().url(url(prefs, "/provider/orders/$id/submit"))
                    .post("{}".toRequestBody(json))
                    .build()
            )
        )

    class BridgeException(val code: Int, val body: String) :
        Exception("bridge HTTP $code: ${body.take(200)}")
}
