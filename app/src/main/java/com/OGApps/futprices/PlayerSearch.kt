package com.OGApps.futprices

//import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.utils.URLEncodedUtils
import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.Image
import android.os.Build
import android.util.Log
import android.widget.ListView
import androidx.annotation.RequiresApi
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.mlkit.vision.text.Text
import org.json.JSONObject
import java.util.stream.Collectors
import java.util.stream.IntStream


object PlayerSearch {
    private const val NOTIFICATION_ID = 1337
    private const val NOTIFICATION_CHANNEL_ID = "com.OGApps.futprices.app"
    private const val NOTIFICATION_CHANNEL_NAME = "com.OGApps.futprices.app"
    lateinit  var appContext: Context

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
        if (result.text.contains("PAC SHO PAS DRI DEF PHY") || result.text.contains("DIV HAN KIC REF SPD POS")) {
            var statKeyBlock =
                result.textBlocks.filter { textBlock -> textBlock.text == "PAC SHO PAS DRI DEF PHY" || textBlock.text == "DIV HAN KIC REF SPD POS" }
            Log.i(FloatingPriceService.TAG, "statKeyBlock: $statKeyBlock")
            var statValueBlock =
                result.textBlocks.filterIndexed { index, _ -> index > 0 && (result.textBlocks[index - 1].text == "PAC SHO PAS DRI DEF PHY" || result.textBlocks[index - 1].text == "DIV HAN KIC REF SPD POS") }//((i,textblock) ->  { textBlock -> textBlock.text == "PAC SHO PAS DRI DEF PHY" })

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
                        //                            val stat = if (statValues[index].size != 6 && index +1 < statValues.size) statValues[index] else statValues[index+1]
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
                    map.remove("DIV")?.let { (map).put("Pace", it) };
                    map.remove("HAN")?.let { (map).put("Shooting", it) };
                    map.remove("KIC")?.let { (map).put("Passing", it) };
                    map.remove("REF")
                    map.remove("SPD")?.let { (map).put("Defending", it) };
                    map.remove("POS")?.let { (map).put("Physicality", it) };
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
        }
        return maps
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
            try {
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
            }
            catch  (e: Exception) {
                return map
            }


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
        var map = HashMap<String, String>()
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
            return map
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
        if (resultText.contains("unassigned", ignoreCase = true) ||
            resultText.contains("my club players", ignoreCase = true)||
            resultText.contains("transfer list", ignoreCase = true) ||
            resultText.contains("player bio", ignoreCase = true)
        ) {
            Log.i(FloatingPriceService.TAG, "this will gather details for multiple players")
//                                Log.i(TAG, "stats: $strStats")
            return true

        } else if (resultText.contains("player details", ignoreCase = true) ||
            resultText.contains("item details", ignoreCase = true)||
            resultText.contains("tem details", ignoreCase = true)
        ) {
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
                // TODO: account for goalkeepers during multisearch
                if (!multiplePlayers(resultText)!!) {
                    val stats = getPlayerStats(resultText)
                    Log.i(FloatingPriceService.TAG, "parsing stats: $stats")

                    if (stats.isEmpty()) {

                        return false
                    } else {
                        getFilteredPlayers( ParseURL.urlEncodeUTF8(stats)){help : JSONObject ->
                            val playerList = arrayOfNulls<Player>(1)
                            val player = Player.getPlayer(help)
                            playerList[0] = player
                            val adapter = PlayerAdapter(appContext, playerList)
                            playerListView.adapter = adapter

//                            Toast.makeText(
//                                this,
//                                "Player: ${player.name} \nPrice: ${player.price}", Toast.LENGTH_SHORT
//                            ).show()
                        }
                    }
                } else {
                    FloatingPriceService.scanImage(FloatingPriceService.convertToBitmap(latestImage))
                    { resultTextFull ->

//                                        val stats = PlayerSearch.getMultiplePlayerStats(resultTextFull)
                        var uncheckedMap = getMultiplePlayerStats(resultTextFull)
                        var maps = uncheckedMap.filter { map -> map != null && map.isNotEmpty() }
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
//                                        Toast.makeText(
//                                            this,
//                                            "Player: ${player.name} \nPrice: ${player.price}",
//                                            Toast.LENGTH_SHORT
//                                        ).show()
                                        if (playerList.filterNotNull().size == maps.size) {
                                            val adapter =
                                                PlayerAdapter(appContext, playerList)
                                            playerListView.adapter = adapter
                                        }
                                    }
                                }

                                for (map in playerList) {
                                    Log.i(FloatingPriceService.TAG, "checking playerList: $map")
                                }
                                Log.i(
                                    FloatingPriceService.TAG,
                                    "adding adapter with: $playerList and ${playerList.size}"
                                )

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
//        Log.i(FloatingPriceService.TAG, "isViewCollapsed: $isViewCollapsed")

        latestImage.close()
        return true
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun getFilteredPlayers(urlParams: String, callback: (JSONObject) -> Unit) {

// Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(appContext)
        val url = "https://www.futbin.org/futbin/api/getFilteredPlayers?${urlParams}"

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                Log.i(FloatingPriceService.TAG, "response here: $response")
                val help = Response(response)
                Log.i(FloatingPriceService.TAG, "response help: $help")
                callback(help)

            },
            { response ->
                Log.i(FloatingPriceService.TAG, "response error: ${response.message}")
            })
        Log.i(FloatingPriceService.TAG, "final url: ${url}")

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