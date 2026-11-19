package com.sushant.lekhak.ai

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Runs an OkHttp call as a cancellable suspend function. */
internal suspend fun OkHttpClient.await(request: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(OpenAINetworkException())
            }
            override fun onResponse(call: Call, response: Response) {
                response.use { cont.resume(it.code to (it.body?.string() ?: "")) }
            }
        })
    }
}

/** Pulls a readable error message out of an API error body. */
internal fun apiErrorMessage(body: String): String = try {
    val obj = JsonParser.parseString(body).asJsonObject
    obj.getAsJsonObject("error")?.get("message")?.asString?.take(160) ?: ""
} catch (_: Exception) {
    ""
}
