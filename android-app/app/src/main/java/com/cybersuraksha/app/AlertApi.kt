package com.cybersuraksha.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Talks to the local scam-pattern alert backend (backend/app.py) for the opt-in "share this
 * report with your district" feature — see DetectTabs/Result screens for where this is offered.
 * This is the one deliberately-online piece of the app: detecting a pattern spreading across
 * *different users* needs a server, which the rest of this offline-first app doesn't use.
 *
 * BASE_URL defaults to localhost, which only works when the backend is reachable from the
 * device — e.g. via `adb reverse tcp:5000 tcp:5000` during development/demo, same as any other
 * local-only hackathon backend. This is not a deployed public endpoint.
 */
object AlertApi {
    var baseUrl: String = "http://localhost:5000"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun submitReport(text: String, districtEn: String, lang: String, verdict: String, source: String, callback: (Boolean) -> Unit) {
        executor.execute {
            val ok = try {
                val url = URL("$baseUrl/api/report")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject()
                    .put("text", text)
                    .put("district", districtEn)
                    .put("lang", lang)
                    .put("verdict", verdict)
                    .put("source", source)
                    .toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                conn.disconnect()
                code in 200..299
            } catch (e: Exception) {
                false
            }
            mainHandler.post { callback(ok) }
        }
    }
}
