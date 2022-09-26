package com.OGApps.futprices

import android.content.Context
import android.media.Image
import android.util.Log
import android.widget.ListView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.mlkit.vision.text.Text
import org.json.JSONObject


object PlayerSearch {
    lateinit  var appContext: Context

//    fun getNotification(context: Context): Pair<Int, Notification> {
//        createNotificationChannel(context)
//        val notification = createNotification(context)
//        val notificationManager =
//            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager.notify(NOTIFICATION_ID, notification)
//        return Pair(NOTIFICATION_ID, notification)
//    }

//    private fun createNotificationChannel(context: Context) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                NOTIFICATION_CHANNEL_ID,
//                NOTIFICATION_CHANNEL_NAME,
//                NotificationManager.IMPORTANCE_LOW
//            )
//            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
//            val manager =
//                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            manager.createNotificationChannel(channel)
//        }
//    }

    private fun getPlayerStats(resultText: String): HashMap<String,String> {
        val map: HashMap<String,String> = if (resultText.contains("GK")) {
            parseGoalkeeper(resultText)
        } else {
            parseOutfielder(resultText)
        }
        return map
    }

    private fun getMultiplePlayerStats(result: Text): Array<HashMap<String, String>?> {
        val maps = arrayOfNulls<HashMap<String, String>>(6)
        if (result.text.contains("((\\s?)+[A-Z]{3}){6}".toRegex()) && result.text.contains("((\\s?)[0-9]{2}){6}".toRegex())) {
            // check regex matches 6 3 letter words
            val keyRegex = "((\\s?)[A-Z]{3}){6}".toRegex()
            // check regex matches 6 2 digit numbers
            val valueRegex = "((\\s?)[0-9]{2}){6}".toRegex()
            val statKeyBlock =
                result.textBlocks.filter { textBlock -> textBlock.text.contains(keyRegex) } as MutableList
            Log.i(FloatingPriceService.TAG, "statKeyBlock: $statKeyBlock")
            val statValueBlock =
                result.textBlocks.filter{ textBlock -> textBlock.text.contains(valueRegex) } as MutableList

            Log.i(FloatingPriceService.TAG, "statValueBlock: $statValueBlock")
            result.textBlocks.forEachIndexed { index, textBlock ->

                Log.i(FloatingPriceService.TAG, "block number $index: ${textBlock.text}") }
            while (statKeyBlock.size > statValueBlock.size)
            {
                statKeyBlock.removeLast()
            }
            while (statKeyBlock.size < statValueBlock.size)
            {
                statValueBlock.removeLast()
            }
            // create a map of the keys and values in the right format to be searched, iterate over and create array
            val statKeys = mutableListOf<List<String>>()
            val statValues = mutableListOf<List<String>>()
            statKeyBlock.forEachIndexed { index, textBlock ->
                Log.i(FloatingPriceService.TAG, "textBlock before: ${textBlock.text}")
                keyRegex.find(statKeyBlock[index].text)?.value?.let {
                    statKeys.add(it.split(" "))
                }
                valueRegex.find(statValueBlock[index].text)?.value?.let { statValues.add(it.split(" ")) }
                Log.i(FloatingPriceService.TAG, "textBlock after: ${keyRegex.find(statKeyBlock[index].text)?.value}")

            }
            statKeys.forEachIndexed{index, keys: List<String> ->
                run {
                    val map = HashMap<String, String>()

                    keys.forEachIndexed { i, key ->
                        val stat = statValues[index]
                        Log.i(FloatingPriceService.TAG, "stat size: ${stat.size}")
                        if (stat.size == 6) {

                            map[key] = "${stat[i]},${stat[i]}"
                        }

                    }
                    map.remove("PAC")?.let { (map).put("Pace", it) }
                    map.remove("SHO")?.let { (map).put("Shooting", it) }
                    map.remove("PAS")?.let { (map).put("Passing", it) }
                    map.remove("DRI")
                    map.remove("DEF")?.let { (map).put("Defending", it) }
                    map.remove("PHY")?.let { (map).put("Physicality", it) }
                    map.remove("DIV")?.let { (map).put("Pace", it) }
                    map.remove("HAN")?.let { (map).put("Shooting", it) }
                    map.remove("KIC")?.let { (map).put("Passing", it) }
                    map.remove("REF")
                    map.remove("SPD")?.let { (map).put("Defending", it) }
                    map.remove("SPP")?.let { (map).put("Defending", it) }
                    map.remove("POS")?.let { (map).put("Physicality", it) }
                    maps[index] = map
                }

            }
//            Log.i(FloatingPriceService.TAG, "statKeyBlock: $statKeys")
//            Log.i(FloatingPriceService.TAG, "statValueBlock: $statValues")
//            Log.i(FloatingPriceService.TAG, "maps: $maps")
//            for (map in maps) {
//                Log.i(FloatingPriceService.TAG, "checking map: $map")
//            }
            return maps
        }
        return maps
    }


    private fun parseOutfielder(resultText: String): HashMap<String, String> {
        val stats: String

        var map = HashMap<String, String>()
        val testValues = arrayOf(" PAC"," SHO"," PAS"," DRI"," DEF"," PHY"," PAY")
        var startIndex = 0
        var endIndex = 0
        for (values in testValues) {
            if (resultText.indexOf(values) > 0) {
                startIndex = if (startIndex< resultText.indexOf(values) && startIndex != 0) startIndex else resultText.indexOf(values)
                endIndex = if (endIndex> resultText.indexOf(values)) endIndex else resultText.indexOf(values)
            }
        }

        startIndex -= 2
        endIndex += 4
        if (startIndex > 0 && endIndex > 0 && startIndex < endIndex) {
            stats = resultText.substring(startIndex, endIndex)
            try {
                map = stats.split("(?<=\\D)(?=\\d)".toRegex()).associateTo(HashMap()) {
                    val (left, right) = it.split(" ")
                    right.trim() to left.trim() + ',' + left.trim()
                }
                map.remove("PAC")?.let { map.put("Pace", it) }
                map.remove("SHO")?.let { map.put("Shooting", it) }
                map.remove("PAS")?.let { map.put("Passing", it) }
                map.remove("DRI")
                map.remove("DEF")?.let { map.put("Defending", it) }
                map.remove("PHY")?.let { map.put("Physicality", it) }
                map.remove("PAY")?.let { map.put("Physicality", it) }
            }
            catch  (e: Exception) {
                return map
            }
        }
        val allowedQuery =
            arrayOf("Pace", "Shooting", "Passing", "Defending", "Physicality", "Physicality")

        val iter: MutableIterator<Map.Entry<String, String>> = map.entries.iterator()
        while (iter.hasNext()) {
            val (key, _) = iter.next()
            if (!allowedQuery.contains(key)) {
                iter.remove()
            }
        }
        return map

    }

    private fun parseGoalkeeper(resultText: String): HashMap<String, String> {
        val stats: String
        var map = HashMap<String, String>()
        val testValues = arrayOf("DIV","HAN","KIC","REF","SPD","POS")
        var startIndex = 0
        var endIndex = 0
        for (values in testValues) {
            if (resultText.indexOf(values) !=0) {
                startIndex = if (startIndex< resultText.indexOf(values) && startIndex != 0) startIndex else resultText.indexOf(values)
                endIndex = if (endIndex> resultText.indexOf(values)) endIndex else resultText.indexOf(values)
            }
        }

        startIndex -= 3
        endIndex += 3
        if (startIndex > 0 && endIndex > 0 && startIndex < endIndex) {
            stats = resultText.substring(startIndex, endIndex)
            Log.i(FloatingPriceService.TAG, "stats: $stats")
            map = stats.split("(?<=\\D)(?=\\d)".toRegex()).associateTo(HashMap()) {
                val (left, right) = it.split(" ")
                right.trim() to left.trim() + ',' + left.trim()
            }

            map.remove("DIV")?.let { map.put("Pace", it) }
            map.remove("HAN")?.let { map.put("Shooting", it) }
            map.remove("KIC")?.let { map.put("Passing", it) }
            map.remove("REF")
            map.remove("SPD")?.let { map.put("Defending", it) }
            map.remove("SPP")?.let { (map).put("Defending", it) }
            map.remove("POS")?.let { map.put("Physicality", it) }

        } else {
            return map
        }
        val allowedQuery =
            arrayOf("Pace", "Shooting", "Passing", "Defending", "Physicality", "Physicality")

        val iter: MutableIterator<Map.Entry<String, String>> = map.entries.iterator()
        while (iter.hasNext()) {
            val (key, _) = iter.next()
            if (!allowedQuery.contains(key)) {
                iter.remove()
            }
        }

        return map
    }


    private fun multiplePlayers(resultText: String): Boolean? {

        val regex = "((\\s?)+[0-9]{2}\\s[A-Z]{3}){6}".toRegex()
        val matches = regex.findAll(resultText)
        var matchCount = 0
        matches.forEach { f ->
            matchCount++
            val m = f.value
            val idx = f.range
            println("$m found at indexes: $idx")
            Log.i(FloatingPriceService.TAG, "$m found at indexes: $idx")

        }
        Log.i(FloatingPriceService.TAG, "$matchCount matches found")
        if (resultText.contains("PAC SHO PAS DRI DEF PHY") || resultText.contains("DIV HAN KIC REF SPD POS")
        ) {
            Log.i(FloatingPriceService.TAG, "this will gather details for multiple players")
//                                Log.i(TAG, "stats: $strStats")
            return true

        } else if (matchCount == 1) {
            return false
        }
        return null
    }

    fun searchPlayer(result: Text, latestImage: Image, playerListView: ListView): Boolean {
        val resultText = result.text
        if (resultText != "") {
            Log.i(FloatingPriceService.TAG, "resultText: $resultText")

            val multiScan = multiplePlayers(resultText)
            if (multiScan != null) {
                if (!multiplePlayers(resultText)!!) {
                    val stats = getPlayerStats(resultText)
                    Log.i(FloatingPriceService.TAG, "parsing stats: $stats")

                    if (stats.isEmpty()) {

                        return false
                    } else {
                        getFilteredPlayers( ParseURL.urlEncodeUTF8(stats)){objPlayer : JSONObject ->
                            val playerList = arrayOfNulls<Player>(1)
                            val player = Player.getPlayer(objPlayer)
                            playerList[0] = player
                            val adapter = PlayerAdapter(appContext, playerList)
                            playerListView.adapter = adapter
                        }
                    }
                } else {
                    FloatingPriceService.scanImage(FloatingPriceService.convertToBitmap(latestImage))
                    { resultTextFull ->

                        val uncheckedMap = getMultiplePlayerStats(resultTextFull)
                        val maps = uncheckedMap.filter { map -> map != null && map.isNotEmpty() }
                            .toTypedArray()
                        val playerList = arrayOfNulls<Player>(maps.size)
                        maps.forEachIndexed { i, map ->
                            Log.i(
                                FloatingPriceService.TAG,
                                "parsing map: $map & ${map != null} & index: $i & size: ${maps.size}"
                            )

                            if (map != null) {
                                getFilteredPlayers(ParseURL.urlEncodeUTF8(map)) { help: JSONObject ->
                                    Log.i(FloatingPriceService.TAG, "getting player with : $help")
                                    if (help.has("data")) {
                                        val player = Player.getPlayer(help)
                                        Log.i(FloatingPriceService.TAG, "player acquired : $player")
                                        Log.i(
                                            FloatingPriceService.TAG,
                                            "adding to playerList: $player"
                                        )

                                        playerList[i] = player

                                        for (map2 in playerList) {
                                            Log.i(
                                                FloatingPriceService.TAG,
                                                "checking playerList: $map2"
                                            )
                                        }
                                        if (playerList.filterNotNull().size == maps.size) {
                                            val adapter =
                                                PlayerAdapter(appContext, playerList)
                                            playerListView.adapter = adapter
                                        }
                                    }
                                }
                            } else {
                                if (playerList.filterNotNull().size == maps.size) {
                                    val adapter =
                                        PlayerAdapter(appContext, playerList)
                                    playerListView.adapter = adapter
                                }
                            }
                        }
                    }
                }
            }
            else
            {
                return false
            }
        } else {
            Log.i(FloatingPriceService.TAG, "no text found: $resultText")
            return false
        }

        latestImage.close()
        return true
    }

    private fun getFilteredPlayers(urlParams: String, callback: (JSONObject) -> Unit) {

        // Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(appContext)
        val url = "https://www.futbin.org/futbin/api/getFilteredPlayers?${urlParams}"

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                Log.i(FloatingPriceService.TAG, "response here: $response")
                val objPlayer = Response(response)
                Log.i(FloatingPriceService.TAG, "response player: $objPlayer")
                callback(objPlayer)

            },
            { response ->
                Log.i(FloatingPriceService.TAG, "response error: ${response.message}")
            })

        // Add the request to the RequestQueue.
        queue.add(stringRequest)

    }


    class Response(json: String) : JSONObject(json) {
        val type: String? = this.optString("type")
        val data = this.optJSONArray("data")
            ?.let { 0.until(it.length()).map { i -> it.optJSONObject(i) } } // returns an array of JSONObject
            ?.map { Foo(it.toString()) } // transforms each JSONObject of the array into Foo
    }

    class Foo(json: String) : JSONObject(json) {
        val id = this.optInt("id")
        val title: String? = this.optString("title")
    }
}