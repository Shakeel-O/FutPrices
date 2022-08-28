package com.OGApps.futprices

import java.io.UnsupportedEncodingException
import java.net.URLEncoder

object ParseURL {

    private fun urlEncodeUTF8(s: String?): String? {
        return try {
            URLEncoder.encode(s, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            throw UnsupportedOperationException(e)
        }
    }

    fun urlEncodeUTF8(map: Map<*, *>?): String {
        val sb = StringBuilder()
        if (map != null) {
            for ((key, value) in map) {
                if (sb.isNotEmpty()) {
                    sb.append("&")
                }
                sb.append(
                    String.format(
                        "%s=%s",
                        urlEncodeUTF8(key.toString()),
                        urlEncodeUTF8(value.toString())
                    )
                )
            }
        }
        return sb.toString()
    }

    fun String.utf8(): String = URLEncoder.encode(this, "UTF-8")
}