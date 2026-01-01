package com.minikano.f50_sms.utils

import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
class KanoRequest {
    companion object {
        private class TimeoutDns(
            private val timeoutMs: Long = 3000
        ) : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val executor = Executors.newSingleThreadExecutor()
                return try {
                    val future = executor.submit<List<InetAddress>> {
                        InetAddress.getAllByName(hostname).toList()
                    }
                    future.get(timeoutMs, TimeUnit.MILLISECONDS)
                } catch (e: Exception) {
                    throw UnknownHostException("DNS timeout: $hostname")
                } finally {
                    executor.shutdown()
                }
            }
        }

        private fun createClient(
            dnsTimeoutMs: Long = 3000,
            connectTimeoutSeconds: Long = 5,
            readTimeoutSeconds: Long = 10,
            writeTimeoutSeconds: Long = 10,
            callTimeoutSeconds: Long? = 15,
        ): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .dns(TimeoutDns(dnsTimeoutMs))
                .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)

            if (callTimeoutSeconds != null) {
                builder.callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
            } else {
                builder.callTimeout(0, TimeUnit.SECONDS)
            }

            return builder.build()
        }

        fun postJson(url: String, json: String): Response {
            val client = createClient()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            return client.newCall(request).execute()
        }

        fun getTextFromUrl(url: String): String? {
            val client = createClient()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            return try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        println("Request failed with code: ${response.code}")
                        return null
                    }
                    response.body?.string()
                }
            } catch (e: Exception) {
                println("请求异常: ${e.message}")
                null
            }
        }

        fun downloadFile(
            url: String,
            outputFile: File,
            onProgress: (percent: Int) -> Unit
        ): String? {
            val client = createClient(
                connectTimeoutSeconds = 10,
                readTimeoutSeconds = 60,
                writeTimeoutSeconds = 60,
                callTimeoutSeconds = null
            )
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    KanoLog.d("kano_ZTE_LOG", "Download failed: ${response.code}")
                    return null
                }

                val body: ResponseBody? = response.body
                if (body == null) {
                    KanoLog.d("kano_ZTE_LOG", "Empty response body")
                    return null
                }

                val contentLength = body.contentLength()
                if (contentLength <= 0) {
                    KanoLog.d("kano_ZTE_LOG", "Invalid content length")
                    return null
                }

                var inputStream: InputStream? = null
                var outputStream: OutputStream? = null

                try {
                    inputStream = body.byteStream()
                    outputStream = FileOutputStream(outputFile)

                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastProgress = 0

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val progress = (100 * totalBytesRead / contentLength).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }

                    outputStream.flush()
                    KanoLog.d("kano_ZTE_LOG", "Download complete: ${outputFile.absolutePath}")
                    return outputFile.absolutePath

                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                } finally {
                    inputStream?.close()
                    outputStream?.close()
                }
            }
        }
    }
}
