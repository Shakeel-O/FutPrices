package com.OGApps.futprices

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class PlayerAdapter(
    private val context: Context,
    private val dataSource: Array<Player?>
) : BaseAdapter() {

    private val inflater: LayoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater


override fun getCount(): Int {
    return dataSource.size
}

//2
override fun getItem(position: Int): Player? {
    return dataSource[position]
}

//3
override fun getItemId(position: Int): Long {
    return position.toLong()
}

//4
@SuppressLint("ViewHolder")
override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    // Get view for row item
    val rowView = inflater.inflate(R.layout.list_item_player, parent, false)
// Get title element
    val coinImageView = rowView.findViewById(R.id.fut_coin) as ImageView

// Get subtitle element
    val playerTextView = rowView.findViewById(R.id.fut_player_name) as TextView

// Get detail element
    val priceTextView = rowView.findViewById(R.id.fut_price) as TextView

    // 1
    val player = getItem(position) as Player
    Log.i(FloatingPriceService.TAG, "getView player: ${player.name} & ${player.price} ")

// 2
    playerTextView.text = player.name
    priceTextView.text = player.price
    coinImageView.setImageResource(R.mipmap.futcoin)
    Log.i(FloatingPriceService.TAG, "did it add: ${playerTextView.text} & ${priceTextView.text} ")


// 3
//    Picasso.with(context).load(recipe.imageUrl).placeholder(R.mipmap.ic_launcher).into(thumbnailImageView)
    return rowView
}
}