package com.sono.kiri

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var startedAt = 0L

    /* Atualização automática: baixa o index.html do GitHub Pages e usa no lugar do que veio no APK.
       A origem continua appassets.androidplatform.net, então os dados (localStorage) não mudam. */
    private val liveFile by lazy { File(filesDir, "web/index.html") }

    private fun prepLive() {
        val prefs = getSharedPreferences("kiri_update", MODE_PRIVATE)
        val vc = try { packageManager.getPackageInfo(packageName, 0).versionCode } catch (e: Exception) { 0 }
        if (prefs.getInt("apk", -1) != vc) {          // APK novo instalado: volta pro HTML que veio nele
            liveFile.delete()
            prefs.edit().putInt("apk", vc).putLong("checked", 0).apply()
        }
    }

    private fun checkUpdate(force: Boolean) {
        val prefs = getSharedPreferences("kiri_update", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("checked", 0) < 30 * 60 * 1000L) return
        prefs.edit().putLong("checked", now).apply()
        Thread {
            try {
                val c = URL("$UPDATE_URL?t=$now").openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 15000
                c.setRequestProperty("Cache-Control", "no-cache")
                if (c.responseCode != 200) return@Thread
                val bytes = c.inputStream.use { it.readBytes() }
                val txt = String(bytes, Charsets.UTF_8)
                if (bytes.size < 50_000 || !txt.contains("KIRI_W") || !txt.contains("KiriBridge")) return@Thread
                val cur = if (liveFile.exists()) liveFile.readBytes() else assets.open("index.html").use { it.readBytes() }
                if (cur.contentEquals(bytes)) return@Thread
                // só aceita se for igual ou mais nova que a que já está no celular
                if (buildOf(txt) < buildOf(String(cur, Charsets.UTF_8))) return@Thread
                liveFile.parentFile?.mkdirs()
                val tmp = File(liveFile.parentFile, "index.tmp")
                tmp.writeBytes(bytes)
                tmp.renameTo(liveFile)
                runOnUiThread {
                    if (System.currentTimeMillis() - startedAt < 10_000) web.reload()
                    else web.evaluateJavascript("window.kiriUpdated && window.kiriUpdated()", null)
                }
            } catch (e: Exception) { }
        }.start()
    }

    private fun buildOf(html: String): Long =
        Regex("name=\"kiri-build\" content=\"(\\d+)\"").find(html)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.addJavascriptInterface(
            Bridge(
                applicationContext,
                { dark -> runOnUiThread { applyBars(dark) } },
                { runOnUiThread { askNotifPermission() } }
            ),
            "KiriBridge"
        )

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.host == "appassets.androidplatform.net" && request.url.path == "/assets/index.html" && liveFile.exists()) {
                    try {
                        return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(liveFile.readBytes()))
                    } catch (e: Exception) { }
                }
                return loader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == "appassets.androidplatform.net") return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                } catch (e: Exception) {
                    true
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(arrayOf())
                fileCallback = filePathCallback
                return try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(fileChooserParams.createIntent(), PICK_FILE)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        startedAt = System.currentTimeMillis()
        prepLive()
        checkUpdate(true)

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }
    }

    private fun askNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), ASK_NOTIF)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyBars(dark: Boolean) {
        val c = if (dark) Color.parseColor("#121B22") else Color.parseColor("#EEF3F6")
        window.statusBarColor = c
        window.navigationBarColor = c
        web.setBackgroundColor(c)
        val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val flags = window.decorView.systemUiVisibility
        window.decorView.systemUiVisibility = if (dark) flags and light.inv() else flags or light
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE) {
            val picked: Array<Uri> = WebChromeClient.FileChooserParams.parseResult(resultCode, data) ?: arrayOf()
            fileCallback?.onReceiveValue(picked)
            fileCallback = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        web.evaluateJavascript("window.pushWidgets && window.pushWidgets()", null)
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        checkUpdate(false)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) {
            web.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    companion object {
        private const val PICK_FILE = 42
        private const val ASK_NOTIF = 43
        private const val UPDATE_URL = "https://94cappu.github.io/Kiri/index.html"
    }
}
