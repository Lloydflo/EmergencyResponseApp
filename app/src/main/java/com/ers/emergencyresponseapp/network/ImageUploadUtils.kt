package com.ers.emergencyresponseapp.network

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

fun uriToProfileImagePart(context: Context, uri: Uri): MultipartBody.Part {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
    val extension = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    val tempFile = File.createTempFile("profile_upload_", ".$extension", context.cacheDir)
    contentResolver.openInputStream(uri)?.use { input ->
        tempFile.outputStream().use { output -> input.copyTo(output) }
    }

    val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
    return MultipartBody.Part.createFormData("profile_image", tempFile.name, requestBody)
}

fun userIdToRequestBody(userId: Int) =
    userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())

fun uriStringToMultipartPart(context: Context, partName: String, fileName: String, uriStr: String): MultipartBody.Part {
    val uri = Uri.parse(uriStr)
    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
    val extension = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    val tempFile = File.createTempFile(fileName, ".$extension", context.cacheDir)
    context.contentResolver.openInputStream(uri)?.use { input ->
        tempFile.outputStream().use { output -> input.copyTo(output) }
    }

    val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
    return MultipartBody.Part.createFormData(partName, tempFile.name, requestBody)
}

fun stringToRequestBody(value: String) =
    value.toRequestBody("text/plain".toMediaTypeOrNull())