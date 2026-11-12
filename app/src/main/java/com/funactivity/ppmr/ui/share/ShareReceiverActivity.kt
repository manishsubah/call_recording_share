package com.funactivity.ppmr.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.funactivity.ppmr.R
import com.funactivity.ppmr.domain.usecase.ImportSharedRecordingsUseCase
import com.funactivity.ppmr.domain.usecase.ShareResult
import com.funactivity.ppmr.domain.usecase.SharedItem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Receives recordings from the share sheet. It has no screen of its own: it copies the files
 * while the share's read grant is still valid, queues them, shows a short message and closes.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject lateinit var importShared: ImportSharedRecordingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        val items = sharedItems(intent)
        lifecycleScope.launch {
            val message = if (items.isEmpty()) {
                getString(R.string.share_nothing_received)
            } else {
                importShared(items).also { Timber.i("Share result: %s", it) }.let(::messageFor)
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun sharedItems(intent: Intent): List<SharedItem> {
        val uris = when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }.ifEmpty {
            val clip = intent.clipData ?: return@ifEmpty emptyList()
            (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        }
        return uris.distinct().map { uri ->
            SharedItem(uri.toString(), runCatching { contentResolver.getType(uri) }.getOrNull() ?: intent.type)
        }
    }

    private fun messageFor(result: ShareResult): String = when (result) {
        ShareResult.NotSignedIn -> getString(R.string.share_sign_in_first)
        is ShareResult.Done -> when {
            result.queued > 0 ->
                resources.getQuantityString(R.plurals.share_queued, result.queued, result.queued)
            result.alreadyInDrive > 0 -> getString(R.string.share_already_in_drive)
            else -> getString(R.string.share_unreadable)
        }
    }
}
