package com.gotify.client.util

import android.util.Log
import com.gotify.client.data.model.ApiResult
import com.google.gson.JsonParser
import retrofit2.Response
import kotlinx.coroutines.CancellationException

private const val TAG = "ApiCall"

suspend fun <T> safeApiCall(call: suspend () -> Response<T>): ApiResult<T> {
    return try {
        val response = call()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ApiResult.Success(body)
            } else {

                ApiResult.Error(response.code(), "Empty response body")
            }
        } else {
            val errorBody = response.errorBody()?.string() ?: "Unknown error"
            Log.w(TAG, "API error ${response.code()}")
            ApiResult.Error(response.code(), parseErrorMessage(errorBody))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Network exception: ${e.message}", e)
        ApiResult.Error(null, e.message ?: "Network error")
    }
}


suspend fun safeApiCallUnit(call: suspend () -> Response<Unit>): ApiResult<Unit> {
    return try {
        val response = call()
        if (response.isSuccessful) {
            ApiResult.Success(Unit)
        } else {
            val errorBody = response.errorBody()?.string() ?: "Unknown error"
            ApiResult.Error(response.code(), parseErrorMessage(errorBody))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Network exception: ${e.message}", e)
        ApiResult.Error(null, e.message ?: "Network error")
    }
}


private fun parseErrorMessage(raw: String): String {
    val parsed = runCatching {
        JsonParser.parseString(raw)
            .asJsonObject
            .get("errorDescription")
            ?.asString
    }.getOrNull()
    return parsed?.takeIf { it.isNotBlank() } ?: raw.trim().take(300)
}
