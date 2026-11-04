package xyz.totemai.app.web

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient

class FileChooserHost {
    private var callback: ValueCallback<Array<Uri>>? = null
    var launch: ((Intent) -> Unit)? = null

    fun begin(cb: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams) {
        callback?.onReceiveValue(null)
        callback = cb

        val intent = runCatching { params.createIntent() }.getOrNull()
        val starter = launch
        if (intent == null || starter == null) {
            finish(null)
            return
        }
        runCatching { starter(intent) }
            .onFailure { finish(null) }
    }

    fun deliver(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            finish(null)
            return
        }

        val uris = when {
            data.clipData != null -> {
                val clip = data.clipData!!
                val selected = mutableListOf<Uri>()
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.let { selected.add(it) }
                }
                selected.toTypedArray()
            }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }
        finish(uris)
    }

    private fun finish(uris: Array<Uri>?) {
        val cb = callback ?: return
        callback = null
        cb.onReceiveValue(uris)
    }
}
