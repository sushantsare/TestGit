package com.sushant.bahikhata.data

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.sushant.bahikhata.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Google Drive backup into the app's hidden "appDataFolder" (only Bahi Khata can see it).
 * Uses Google's Authorization API: no password or token is stored by the app.
 */
object Drive {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val NAME = "bahikhata-backup.bkb"

    sealed class Res { object Ok : Res(); data class NeedsSignIn(val intent: PendingIntent?) : Res(); data class Fail(val msg: String) : Res() }

    fun request(): AuthorizationRequest = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Returns a token, or the PendingIntent the UI must launch once to ask for consent. */
    fun token(context: Context): Pair<String?, PendingIntent?> {
        val r: AuthorizationResult = Tasks.await(Identity.getAuthorizationClient(context).authorize(request()), 30, TimeUnit.SECONDS)
        return if (r.hasResolution()) null to r.pendingIntent else r.accessToken to null
    }

    suspend fun backup(context: Context): Res = withContext(Dispatchers.IO) {
        val app = context.applicationContext as App
        val pw = Backup.loadPassword(context) ?: return@withContext Res.Fail("Set a backup password in Bahi Khata → Backup")
        val (tok, pi) = try { token(context) } catch (e: Exception) { return@withContext Res.Fail("Google sign-in isn't available: " + (e.message ?: "unknown error")) }
        if (tok == null) return@withContext Res.NeedsSignIn(pi)
        try {
            val bytes = Backup.build(context, pw)
            val existing = findId(tok)
            val boundary = "bk" + System.nanoTime()
            val meta = if (existing == null) """{"name":"$NAME","parents":["appDataFolder"]}""" else """{"name":"$NAME"}"""
            val body = ByteArrayOutputStream().apply {
                write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
                write(bytes); write("\r\n--$boundary--\r\n".toByteArray())
            }.toByteArray()
            val url = if (existing == null) "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
            else "https://www.googleapis.com/upload/drive/v3/files/$existing?uploadType=multipart"
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = if (existing == null) "POST" else "PATCH"
                if (existing != null) { requestMethod = "POST"; setRequestProperty("X-HTTP-Method-Override", "PATCH") }
                doOutput = true; connectTimeout = 20_000; readTimeout = 60_000
                setRequestProperty("Authorization", "Bearer $tok")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            }
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            if (code !in 200..299) return@withContext Res.Fail("Drive said $code")
            app.prefs.update { it.copy(lastDriveBackup = System.currentTimeMillis()) }
            Res.Ok
        } catch (e: Exception) { Res.Fail(e.message ?: "Network problem") }
    }

    data class Remote(val id: String, val modified: String, val size: Long)

    suspend fun latest(context: Context): Pair<Remote?, Res> = withContext(Dispatchers.IO) {
        val (tok, pi) = try { token(context) } catch (e: Exception) { return@withContext null to Res.Fail(e.message ?: "Google sign-in isn't available") }
        if (tok == null) return@withContext null to Res.NeedsSignIn(pi)
        try {
            val c = URL("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,modifiedTime,size)&q=name%3D'$NAME'").openConnection() as HttpURLConnection
            c.setRequestProperty("Authorization", "Bearer $tok")
            val arr = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("files")
            if (arr.length() == 0) return@withContext null to Res.Ok
            val f = arr.getJSONObject(0)
            Remote(f.getString("id"), f.optString("modifiedTime"), f.optString("size", "0").toLongOrNull() ?: 0L) to Res.Ok
        } catch (e: Exception) { null to Res.Fail(e.message ?: "Network problem") }
    }

    suspend fun download(context: Context, id: String): ByteArray = withContext(Dispatchers.IO) {
        val tok = token(context).first ?: throw IllegalStateException("Sign in to Google Drive first")
        val c = URL("https://www.googleapis.com/drive/v3/files/$id?alt=media").openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer $tok")
        c.inputStream.use { it.readBytes() }
    }

    private fun findId(tok: String): String? = runCatching {
        val c = URL("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id)&q=name%3D'$NAME'").openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer $tok")
        val arr = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("files")
        if (arr.length() > 0) arr.getJSONObject(0).getString("id") else null
    }.getOrNull()
}
