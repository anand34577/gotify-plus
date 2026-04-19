package com.gotify.client.util

import android.util.Log
import com.gotify.client.data.model.ApiResult
import retrofit2.Response

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
            Log.w(TAG, "API error ${response.code()}: $errorBody")
            ApiResult.Error(response.code(), parseErrorMessage(errorBody))
        }
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
    } catch (e: Exception) {
        Log.e(TAG, "Network exception: ${e.message}", e)
        ApiResult.Error(null, e.message ?: "Network error")
    }
}


private fun parseErrorMessage(raw: String): String {
    return try {
        val regex = """"errorDescription"\s*:\s*"([^"]+)"""".toRegex()
        regex.find(raw)?.groupValues?.get(1) ?: raw
    } catch (e: Exception) {
        raw
    }
}