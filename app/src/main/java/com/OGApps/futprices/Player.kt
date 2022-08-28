package com.OGApps.futprices

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

class Player(
    val name: String,
    val price: String) {

    companion object {

        fun getPlayer(playerJSON: JSONObject): Player {
            val player: Player

                // Load data
//                val jsonString = loadJsonFromAsset("recipes.json", context)
//                val json = JSONObject(jsonString)
                val data = playerJSON.getJSONArray("data")

                // Get Recipe objects from data
            player = Player(
                data.getJSONObject(0).getString("playername"),
                if (data.getJSONObject(0).getString("ps_LCPrice") == "null")
                    "0" else "%,d".format(data.getJSONObject(0).getInt("ps_LCPrice"))
            )


            return player
        }

        private fun loadJsonFromAsset(filename: String, context: Context): String? {
            var json: String? = null

            try {
                val inputStream = context.assets.open(filename)
                val size = inputStream.available()
                val buffer = ByteArray(size)
                inputStream.read(buffer)
                inputStream.close()
                json = String(buffer, Charsets.UTF_8)
            } catch (ex: java.io.IOException) {
                ex.printStackTrace()
                return null
            }

            return json
        }
    }
}