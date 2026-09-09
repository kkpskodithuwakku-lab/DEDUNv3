package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.ui.theme.MyApplicationTheme
import java.io.File

class AndroidBridge(private val activity: Activity) {
  @JavascriptInterface
  fun isNativeApp(): Boolean = true

  @JavascriptInterface
  fun shareText(text: String, title: String = "Share") {
    activity.runOnUiThread {
      try {
        val intent = Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_SUBJECT, title)
          putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, title))
      } catch (e: Exception) {
        Log.e("AndroidBridge", "Error sharing text", e)
        Toast.makeText(activity, "Unable to share text", Toast.LENGTH_SHORT).show()
      }
    }
  }

  @JavascriptInterface
  fun copyToClipboard(text: String, label: String = "Ledger Data") {
    activity.runOnUiThread {
      try {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(activity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
      } catch (e: Exception) {
        Log.e("AndroidBridge", "Error copying to clipboard", e)
      }
    }
  }

  @JavascriptInterface
  fun shareFile(base64Data: String, mimeType: String, fileName: String, title: String = "Share File") {
    activity.runOnUiThread {
      try {
        val cleanBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        val exportDir = File(activity.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val file = File(exportDir, fileName)
        file.writeBytes(bytes)

        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
          type = mimeType
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_SUBJECT, title)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, title))
      } catch (e: Exception) {
        Log.e("AndroidBridge", "Error sharing file", e)
        Toast.makeText(activity, "Unable to share file: ${e.message}", Toast.LENGTH_SHORT).show()
      }
    }
  }

  @JavascriptInterface
  fun saveFile(base64Data: String, mimeType: String, fileName: String): Boolean {
    var success = false
    try {
      val cleanBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
      val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val isImage = mimeType.startsWith("image/")
        val contentValues = ContentValues().apply {
          put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
          put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
          if (isImage) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DedunLedger")
          } else {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DedunLedger")
          }
        }
        val collection = if (isImage) {
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
          MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val uri = activity.contentResolver.insert(collection, contentValues)
        if (uri != null) {
          activity.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(bytes)
          }
          success = true
        }
      }

      if (!success) {
        // Fallback saving to app's external files directory
        val extDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.filesDir
        val targetDir = File(extDir, "DedunLedger")
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, fileName)
        targetFile.writeBytes(bytes)
        success = true
      }

      activity.runOnUiThread {
        if (success) {
          val destMsg = if (mimeType.startsWith("image/")) "Saved to Pictures/DedunLedger" else "Saved to Downloads/DedunLedger"
          Toast.makeText(activity, "$fileName saved ($destMsg)", Toast.LENGTH_LONG).show()
        } else {
          Toast.makeText(activity, "Failed to save $fileName", Toast.LENGTH_SHORT).show()
        }
      }
    } catch (e: Exception) {
      Log.e("AndroidBridge", "Error saving file", e)
      activity.runOnUiThread {
        Toast.makeText(activity, "Error saving file: ${e.message}", Toast.LENGTH_SHORT).show()
      }
      return false
    }
    return success
  }
}

class MainActivity : ComponentActivity() {
  private var webView: WebView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        if (webView?.canGoBack() == true) {
          webView?.goBack()
        } else {
          isEnabled = false
          onBackPressedDispatcher.onBackPressed()
        }
      }
    })

    setContent {
      MyApplicationTheme {
        Scaffold(
          modifier = Modifier
            .fillMaxSize()
            .testTag("ledger_root_scaffold"),
          contentWindowInsets = WindowInsets.systemBars
        ) { innerPadding ->
          LedgerWebView(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
              .imePadding()
              .testTag("ledger_webview_container"),
            onWebViewCreated = { createdWebView ->
              webView = createdWebView
            }
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    webView?.onResume()
  }

  override fun onPause() {
    super.onPause()
    webView?.onPause()
  }

  override fun onDestroy() {
    webView?.let {
      it.stopLoading()
      it.destroy()
    }
    webView = null
    super.onDestroy()
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LedgerWebView(
  modifier: Modifier = Modifier,
  onWebViewCreated: (WebView) -> Unit = {}
) {
  var webViewRecoveryKey by remember { mutableIntStateOf(0) }

  Box(
    modifier = modifier.background(ComposeColor(0xFF1C, 0x1B, 0x17))
  ) {
    key(webViewRecoveryKey) {
      AndroidView(
        modifier = Modifier
          .fillMaxSize()
          .testTag("ledger_webview"),
        factory = { context ->
          WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#1C1B17"))
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            // Avoid forced hardware offscreen layer buffers that trigger DRM rendernode checks
            setLayerType(View.LAYER_TYPE_NONE, null)
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              databaseEnabled = true
              allowFileAccess = true
              allowContentAccess = true
              cacheMode = WebSettings.LOAD_DEFAULT
              useWideViewPort = true
              loadWithOverviewMode = true
              mediaPlaybackRequiresUserGesture = true
            }
            webViewClient = object : WebViewClient() {
              override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                val crashed = detail?.didCrash() ?: false
                Log.w("LedgerWebView", "WebView render process gone (didCrash: $crashed). Recovering view...")
                try {
                  view?.let { deadView ->
                    (deadView.parent as? ViewGroup)?.removeView(deadView)
                    deadView.destroy()
                  }
                } catch (e: Exception) {
                  Log.e("LedgerWebView", "Error cleaning up dead WebView", e)
                }
                // Trigger Compose to recreate a fresh WebView
                webViewRecoveryKey++
                return true
              }
            }
            webChromeClient = object : WebChromeClient() {
              override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                android.util.Log.d("DedunWebView", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                return super.onConsoleMessage(consoleMessage)
              }
            }
            if (context is Activity) {
              addJavascriptInterface(AndroidBridge(context), "AndroidBridge")
            }
            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
              try {
                if (url.startsWith("data:") || url.startsWith("blob:")) {
                  // Handled internally by JavascriptInterface or in-app dialog
                } else {
                  val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(url)
                  }
                  context.startActivity(intent)
                }
              } catch (e: Exception) {
                Log.e("LedgerWebView", "Error handling download", e)
              }
            }
            loadUrl("file:///android_asset/index.html")
            onWebViewCreated(this)
          }
        }
      )
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("Android") }
}

