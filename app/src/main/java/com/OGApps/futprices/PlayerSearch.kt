package com.OGApps.futprices

//import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.utils.URLEncodedUtils
import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.mlkit.vision.text.Text
import java.util.stream.Collectors
import java.util.stream.IntStream


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

    fun getPlayerStats(resultText: String): HashMap<String,String> {
        var stats = ""
        var map: HashMap<String,String>

            if (resultText.contains("GK")) {
                map = parseGoalkeeper(resultText)
            } else {
                map = parseOutfielder(resultText)
            }
        return map
    }

    fun getMultiplePlayerStats(result: Text): Array<HashMap<String, String>?> {
        var stats = ""
        var maps = arrayOfNulls<HashMap<String, String>>(6)
        if (result.text.contains("PAC SHO PAS DRI DEF PHY")) {
            var statKeyBlock =
                result.textBlocks.filter { textBlock -> textBlock.text == "PAC SHO PAS DRI DEF PHY" }
            Log.i(FloatingPriceService.TAG, "statKeyBlock: $statKeyBlock")
            var statValueBlock =
                result.textBlocks.filterIndexed { index, textBlock -> index > 0 && result.textBlocks[index - 1].text == "PAC SHO PAS DRI DEF PHY" }//((i,textblock) ->  { textBlock -> textBlock.text == "PAC SHO PAS DRI DEF PHY" })

            Log.i(FloatingPriceService.TAG, "statValueBlock: $statValueBlock")

            //TODO: create a map of the keys and values in the right format to be searched, iterate over and crearte array
            var statKeys = mutableListOf<List<String>>()
            var statValues = mutableListOf<List<String>>()
            statKeyBlock.forEachIndexed { index, textBlock -> statKeys.add(statKeyBlock[index].text.split(" ")); statValues.add(statValueBlock[index].text.split(" ")) }
            statKeys.forEachIndexed{index, keys: List<String> ->
                run {
                    var map = HashMap<String, String>()

                    keys.forEachIndexed { i, key ->
                            val stat = statValues[index]
                        Log.i(FloatingPriceService.TAG, "stat size: ${stat.size}")
                        if (stat.size == 6) {

                            map[key] = "${stat[i]},${stat[i]}"
                        }

                    }
                    map.remove("PAC")?.let { (map).put("Pace", it) };
                    map.remove("SHO")?.let { (map).put("Shooting", it) };
                    map.remove("PAS")?.let { (map).put("Passing", it) };
                    map.remove("DRI")
                    map.remove("DEF")?.let { (map).put("Defending", it) };
                    map.remove("PHY")?.let { (map).put("Physicality", it) };
                    Log.i(FloatingPriceService.TAG, "adding map: $map")
                    maps[index] = map
                }

            }
            Log.i(FloatingPriceService.TAG, "statKeyBlock: $statKeys")
            Log.i(FloatingPriceService.TAG, "statValueBlock: $statValues")
            Log.i(FloatingPriceService.TAG, "maps: $maps")
            for (map in maps) {
                Log.i(FloatingPriceService.TAG, "checking map: $map")
            }
            return maps
//            if (resultText.contains("GK")) {
//                map = parseGoalkeeper(resultText)
//            } else {
//                map = parseOutfielder(resultText)
//            }
//    return zipToMap(statTitle,stat)
        }
        return maps
    }

    fun <K, V> zipToMap(keys: List<K>, values: List<V>): HashMap<K, V>? {
        val keyIter = keys.iterator()
        val valIter = values.iterator()
        return HashMap(IntStream.range(0, keys.size).boxed()
            .collect(Collectors.toMap({ _i -> keyIter.next() }) { _i -> valIter.next() }))
    }


    private fun parseOutfielder(resultText: String): HashMap<String, String> {
        var stats = ""

        var map = HashMap<String, String>()
        val testValues = arrayOf("PAC","SHO","PAS","DRI","DEF","PHY","PAY")
        var startIndex = 0
        var endIndex = 0
        for (values in testValues) {
            Log.i(FloatingPriceService.TAG, "about to test: ${values} index: ${resultText.indexOf(values)}")
            if (resultText.indexOf(values) > 0) {
                startIndex = if (startIndex< resultText.indexOf(values) && startIndex != 0) startIndex else resultText.indexOf(values)
                endIndex = if (endIndex> resultText.indexOf(values)) endIndex else resultText.indexOf(values)
            }
        }
        Log.i(FloatingPriceService.TAG, "startindex: ${startIndex} endIndex: $endIndex")

        startIndex -= 3
        endIndex += 3
        if (startIndex > 0 && endIndex > 0 && startIndex < endIndex) {
            stats = resultText.substring(startIndex, endIndex)
            Log.i(FloatingPriceService.TAG, "stats: $stats")
            map = stats.split("(?<=\\D)(?=\\d)".toRegex()).associateTo(HashMap()) {
                val (left, right) = it.split(" ")
                right.trim() to left.trim() + ',' + left.trim()
            }
            map.remove("PAC")?.let { map.put("Pace", it) }
            map.remove("SHO")?.let { map.put("Shooting", it) }
            map.remove("PAS")?.let { map.put("Passing", it) }
            map.remove("DRI")
//                map.remove("DRI")?.let { map.put("Dribbling", it) }
            map.remove("DEF")?.let { map.put("Defending", it) }
            map.remove("PHY")?.let { map.put("Physicality", it) }
            map.remove("PAY")?.let { map.put("Physicality", it) }
        } else {
//            map = stats.split("(?<=\\D)(?=\\d)".toRegex()).associateTo(HashMap()) {
//                val (left, right) = it.split(" ")
//                right.trim() to left.trim() + ',' + left.trim()
//            }
        }
        val allowedQuery =
            arrayOf("Pace", "Shooting", "Passing", "Defending", "Physicality", "Physicality")

//        for (values in map) {
//            if (!allowedQuery.contains(values.key)) {
//                Log.i(FloatingPriceService.TAG, "removing: ${values.key}")
//                map.remove(values.key)
//            }
//        }
        val iter: MutableIterator<Map.Entry<String, String>> = map.entries.iterator()
        while (iter.hasNext()) {
            val (key, value) = iter.next()
            Log.i(FloatingPriceService.TAG, "removing: ${key} or $value")
            if (!allowedQuery.contains(key)) {
                iter.remove()
            }
        }
        return map

    }

    fun parseGoalkeeper(resultText: String): HashMap<String, String> {
        var stats = ""
        var map: HashMap<String, String>
        val testValues = arrayOf("DIV","HAN","KIC","REF","SPD","POS")
        var startIndex = 0
        var endIndex = 0
        for (values in testValues) {
            Log.i(FloatingPriceService.TAG, "about to test: ${values} index: ${resultText.indexOf(values)}")
            if (resultText.indexOf(values) !=0) {
                startIndex = if (startIndex< resultText.indexOf(values) && startIndex != 0) startIndex else resultText.indexOf(values)
                endIndex = if (endIndex> resultText.indexOf(values)) endIndex else resultText.indexOf(values)
            }
        }
        Log.i(FloatingPriceService.TAG, "startindex: ${startIndex} endIndex: $endIndex")

        startIndex -= 3
        endIndex += 3
        if (startIndex!! > 0 && endIndex!! > 0 && startIndex!! < endIndex!!) {
            stats = resultText.substring(startIndex, endIndex)
            Log.i(FloatingPriceService.TAG, "stats: $stats")
            map = stats.split("(?<=\\D)(?=\\d)".toRegex()).associateTo(HashMap()) {
                val (left, right) = it.split(" ")
                right.trim() to left.trim() + ',' + left.trim()
            }

            map.remove("DIV")?.let { map.put("Pace", it) }
            map.remove("HAN")?.let { map.put("Shooting", it) }
            map.remove("KIC")?.let { map.put("Passing", it) }
//            map.remove("REF").let {map.put("Dribbling",it)
            map.remove("REF")
            map.remove("SPD")?.let { map.put("Defending", it) }
            map.remove("POS")?.let { map.put("Physicality", it) }

        } else {
            map = emptyMap<String, String>() as HashMap<String, String>
        }
        val allowedQuery =
            arrayOf("Pace", "Shooting", "Passing", "Defending", "Physicality", "Physicality")

        val iter: MutableIterator<Map.Entry<String, String>> = map.entries.iterator()
        while (iter.hasNext()) {
            val (key, value) = iter.next()
            Log.i(FloatingPriceService.TAG, "removing: ${key} or $value")
            if (!allowedQuery.contains(key)) {
                iter.remove()
            }
        }

        return map
    }


    fun multiplePlayers(resultText: String): Boolean? {
        if (resultText.contains("player details", ignoreCase = true) ||
            resultText.contains("item details", ignoreCase = true)||
            resultText.contains("tem details", ignoreCase = true)
        ) {
            return false
        } else if (resultText.contains("unassigned", ignoreCase = true) ||
            resultText.contains("my club players", ignoreCase = true)||
            resultText.contains("transfer list", ignoreCase = true)
        ) {
            Log.i(FloatingPriceService.TAG, "this will gather details for multiple players")
//                                Log.i(TAG, "stats: $strStats")
            return true

        }
        return null
    }

}