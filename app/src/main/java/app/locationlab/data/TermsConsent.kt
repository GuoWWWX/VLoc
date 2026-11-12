package app.locationlab.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object TermsConsent {
    private const val REVISION = 1
    private const val ACCEPTED_REVISION = "accepted_revision"

    fun hasAccepted(context: Context): Boolean =
        preferences(context).getInt(ACCEPTED_REVISION, 0) >= REVISION

    suspend fun accept(context: Context): Boolean = withContext(Dispatchers.IO) {
        preferences(context).edit().putInt(ACCEPTED_REVISION, REVISION).commit()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences("vloc_legal", Context.MODE_PRIVATE)
}
