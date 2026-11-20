package se.minbudget.app

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import java.io.File

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var pendingJs: String? = null
    private var loaded = false

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(req.url)
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url
                if (u.host == "appassets.androidplatform.net") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: Exception) { }
                return true
            }
            override fun onPageFinished(view: WebView, url: String) {
                loaded = true
                pendingJs?.let { view.evaluateJavascript(it, null) }
                pendingJs = null
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null); fileCb = cb
                return try { startActivityForResult(p.createIntent(), 7); true } catch (e: Exception) { fileCb = null; false }
            }
        }
        val hash = if (intent?.action == ACTION_QUICK) "#snabb" else ""
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html$hash")
        handle(intent, false)
        PayReminders.scheduleNext(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent, true)
    }

    private fun handle(i: Intent?, fresh: Boolean) {
        if (i == null) return
        when {
            i.getBooleanExtra(PayReminders.EXTRA_PAY, false) -> run("window.payNow && payNow()")
            fresh && i.action == ACTION_QUICK -> run("location.hash='#snabb'")
        }
    }

    private fun run(js: String) { if (loaded) web.evaluateJavascript(js, null) else pendingJs = js }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 7) {
            fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data)); fileCb = null
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("pay", MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
    }

    inner class Bridge {
        @JavascriptInterface
        fun setPayReminders(json: String) {
            PayReminders.save(this@MainActivity, json)
            if (PayReminders.items(this@MainActivity).isNotEmpty()) runOnUiThread { askNotifications() }
        }

        @JavascriptInterface
        fun saveFile(name: String, content: String) {
            runOnUiThread {
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        val v = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, name)
                            put(MediaStore.Downloads.MIME_TYPE, "application/json")
                        }
                        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
                        contentResolver.openOutputStream(uri)!!.use { it.write(content.toByteArray()) }
                        Toast.makeText(this@MainActivity, "Kopian är sparad i Hämtade filer", Toast.LENGTH_LONG).show()
                    } else {
                        val dir = File(cacheDir, "shared").apply { mkdirs() }
                        val f = File(dir, name); f.writeText(content)
                        val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", f)
                        val s = Intent(Intent.ACTION_SEND).setType("application/json")
                            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        startActivity(Intent.createChooser(s, "Spara kopian"))
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Kunde inte spara kopian", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun shareApp() {
            runOnUiThread {
                val s = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Prova Min budget, en enkel budgetapp för Android: https://github.com/fredriklofsten/min-budget")
                startActivity(Intent.createChooser(s, "Dela appen"))
            }
        }
    }

    companion object { const val ACTION_QUICK = "se.minbudget.app.QUICK" }
}
