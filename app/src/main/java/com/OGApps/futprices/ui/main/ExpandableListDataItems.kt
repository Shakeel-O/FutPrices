package com.OGApps.futprices.ui.main

object ExpandableListDataItems {
    val data: HashMap<String, List<String>>
        get() {
            val expandableDetailList = HashMap<String, List<String>>()

            // Add Answers
            val q1: MutableList<String> = ArrayList()
            q1.add("Please make sure the permissions \"draw overlays\" and \"screen capture\" are set to ON")
            val q2: MutableList<String> = ArrayList()
            q2.add("Using the FUT web/companion app, select a player and click on the fut prices icon. This should show the right pricing for the player")
            val q3: MutableList<String> = ArrayList()
            q3.add("This App should work for Android 8 (Oreo) and later")
            val q4: MutableList<String> = ArrayList()
            q3.add("This App should work for Android 8 (Oreo) and later")
            val q5: MutableList<String> = ArrayList()
            q3.add("This App should work for Android 8 (Oreo) and later")

            // Assign questions to answers
            expandableDetailList["Is my phone Compatible with this app?"] = q3
            expandableDetailList["How do I capture the correct price?"] = q2
            expandableDetailList["Why can't I press the start button?"] = q1
            return expandableDetailList
        }
}