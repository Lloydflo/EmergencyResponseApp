package com.ers.emergencyresponseapp.network

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

fun uriToProfileImagePart(context: Context, uri: Uri): MultipartBody.Part {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
    val extension = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    val tempFile = File.createTempFile("profile_upload_", ".$extension", context.cacheDir)
    val input = contentResolver.openInputStream(uri)
        ?: throw FileNotFoundException("The selected profile image cannot be opened")
    input.use {
        tempFile.outputStream().use { output -> input.copyTo(output) }
    }
    if (tempFile.length() == 0L) {
        tempFile.delete()
        throw IOException("The selected profile image is empty")
    }

    val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
    return MultipartBody.Part.createFormData("profile_image", tempFile.name, requestBody)
}

fun userIdToRequestBody(userId: Int) =
    userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())

fun uriStringToMultipartPart(
    context: Context,
    partName: String,
    fileName: String,
    uriStr: String
): MultipartBody.Part {

    val uri = Uri.parse(uriStr)

    val file: File

    val mimeType: String

    if (uri.scheme.equals("file", ignoreCase = true)) {

        val path = uri.path?.takeIf { it.isNotBlank() }
            ?: throw FileNotFoundException("The selected image path is invalid")
        file = File(path)
        if (!file.isFile || !file.canRead()) {
            throw FileNotFoundException("The selected image cannot be opened")
        }

        mimeType = "image/jpeg"

    } else {

        mimeType =
            context.contentResolver.getType(uri)
                ?: "image/jpeg"

        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }

        file = File.createTempFile(
            fileName,
            ".$extension",
            context.cacheDir
        )

        val input = context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("The selected image cannot be opened")
        input.use {
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
    }

    if (file.length() == 0L) {
        if (!uri.scheme.equals("file", ignoreCase = true)) file.delete()
        throw IOException("The selected image is empty")
    }

    val requestBody =
        file.asRequestBody(
            mimeType.toMediaTypeOrNull()
        )

    return MultipartBody.Part.createFormData(
        partName,
        file.name,
        requestBody
    )
}

fun stringToRequestBody(value: String) =
    value.toRequestBody("text/plain".toMediaTypeOrNull())
