package com.myhub.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves exactly one file - the APK [Updater] just downloaded - to Android's installer.
 *
 * Since Android 7 a file:// URI cannot be handed to another app (FileUriExposedException), and the
 * usual answer is androidx FileProvider. The hub carries no libraries on purpose, so the one method
 * that matters is written here instead: openFile() hands back a read-only descriptor for a single
 * name inside the hub's own cache, and nothing else on the phone is reachable through this
 * provider. It is not exported, and the read is granted per-intent, not to everyone.
 */
class ApkProvider : ContentProvider() {

    companion object {
        const val NAME = "myhub-update.apk"

        fun authority(ctx: Context) = "${ctx.packageName}.updates"

        fun fileFor(ctx: Context) = File(File(ctx.cacheDir, "updates"), NAME)

        fun uriFor(ctx: Context): Uri = Uri.parse("content://${authority(ctx)}/$NAME")
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri.lastPathSegment != NAME) throw FileNotFoundException("only $NAME is served")
        val ctx = context ?: throw FileNotFoundException("no context")
        val f = fileFor(ctx)
        if (!f.exists()) throw FileNotFoundException("no update has been downloaded")
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
