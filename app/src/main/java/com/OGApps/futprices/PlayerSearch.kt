package com.OGApps.futprices

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
//import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.utils.URLEncodedUtils
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLEncoder

object PlayerSearch {
    private const val NOTIFICATION_ID = 1337
    private const val NOTIFICATION_CHANNEL_ID = "com.OGApps.futprices.app"
    private const val NOTIFICATION_CHANNEL_NAME = "com.OGApps.futprices.app"
//    fun getNotification(context: Context): Pair<Int, Notification> {
//        createNotificationChannel(context)
//        val notification = createNotification(context)
//        val notificationManager =
//            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager.notify(NOTIFICATION_ID, notification)
//        return Pair(NOTIFICATION_ID, notification)
//    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun getPlayerStats(resultText: String): String {
        var stats = ""
        var map: HashMap<String,String>
        if (resultText.contains("player details", ignoreCase = true) ||
            resultText.contains("item details", ignoreCase = true)
        ) {
            /* example of successful screenshot
            *     O 30 A4 14%
                    PLAYER DETAILS
                    472,505 0
                    95
                    ST
                    Arsenal
                    p
                    LACAZETTE
                    94 PAC
                    96 SHO
                    88 PAS
                    95 DRI
                    57 DEF
                    93 PHY
                    POS: ST
                    * https://www.futbin.org/futbin/api/getFilteredPlayers?Passing=99&Pace=96&league=13&position=LW*/
            val startIndex = resultText.indexOf("PAC") - 3
            val endIndex = resultText.indexOf("POS") - 1
            if (startIndex > 0 && endIndex > 0 && startIndex < endIndex) {
                stats = resultText.substring(startIndex, endIndex)
                Log.i(FloatingPriceService.TAG, "stats: $stats")
                map = stats.split("(?<=\\D)(?=\\d)".toRegex()).associateTo(HashMap()) {
                    val (left, right) = it.split(" ")
                    right.trim() to left.trim()+','+left.trim()
                }
                map.remove("PAC")?.let { map.put("Pace", it) };
                map.remove("SHO")?.let { map.put("Shooting", it) };
                map.remove("PAS")?.let { map.put("Passing", it) };
                map.remove("DRI");
//                map.remove("DRI")?.let { map.put("Dribbling", it) };
                map.remove("DEF")?.let { map.put("Defending", it) };
                map.remove("PHY")?.let { map.put("Physicality", it) };
                map.remove("PAY")?.let { map.put("Physicality", it) };
//                map["position"] = resultText.substring(endIndex+6, resultText.count())
                Log.i(FloatingPriceService.TAG, "map: $map mapString: ${urlEncodeUTF8(map)}")
//                getFilteredPlayers(urlEncodeUTF8(map))
//                val hmm = stats.split("(?<=\\D)(?=\\d)".toRegex())
//                Log.i(FloatingPriceService.TAG, "split: $hmm, \n length: ${hmm.count()}")
//                val str = "abcd1234"
//                val part = str.split("(?<=\\D)(?=\\d)".toRegex()).toTypedArray()
//                Log.i(FloatingPriceService.TAG, "part: $part, \n length: ${part.count()}")3
                stats = urlEncodeUTF8(map)
            }
        }
        else if (resultText.contains("unassigned", ignoreCase = true) ||
            resultText.contains("my club players", ignoreCase = true)
        ) {
            Log.i(FloatingPriceService.TAG, "this will gather details for multiple players")
//                                Log.i(TAG, "stats: $strStats")

        }

        return stats
    }

    fun urlEncodeUTF8(s: String?): String? {
        return try {
            URLEncoder.encode(s, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            throw UnsupportedOperationException(e)
        }
    }

    private fun urlEncodeUTF8(map: Map<*, *>): String {
        val sb = StringBuilder()
        for ((key, value) in map) {
            if (sb.length > 0) {
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
        return sb.toString()
    }

    fun String.utf8(): String = URLEncoder.encode(this, "UTF-8")

}