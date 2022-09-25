package com.OGApps.futprices

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

class Player(
    val name: String,
    val price: String,
    val id: Int) {

    companion object {

        fun getPlayer(playerJSON: JSONObject): Player {
            val player: Player

                // Load data
//                val jsonString = loadJsonFromAsset("recipes.json", context)
//                val json = JSONObject(jsonString)
                val data = playerJSON.getJSONArray("data")

                // Get right objects from data
            player = Player(
                data.getJSONObject(0).getString("playername"),
                if (data.getJSONObject(0).getString("ps_LCPrice") == "null")
                    "0" else "%,d".format(data.getJSONObject(0).getInt("ps_LCPrice")),
                data.getJSONObject(0).getInt("ID")
            )


            return player
        }

    }
}