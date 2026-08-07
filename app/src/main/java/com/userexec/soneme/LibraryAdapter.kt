package com.userexec.soneme

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class LibraryAdapter(private val context: Context) : BaseAdapter() {
    private val items = mutableListOf<LibraryEntry>()

    fun replace(newItems: List<LibraryEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun item(position: Int): LibraryEntry? = items.getOrNull(position)
    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.row_library_entry, parent, false)
        val item = items[position]
        view.findViewById<TextView>(R.id.rowTitle).text = if (item.isFolder) "▸ ${item.title}" else item.title
        view.findViewById<TextView>(R.id.rowSubtitle).text = item.subtitle
        view.findViewById<TextView>(R.id.rowDuration).text = if (item.isFolder) "" else Formatters.compactDuration(item.durationMs)
        view.findViewById<TextView>(R.id.rowProgress).text = item.progressPercent?.let { "$it%" } ?: ""
        return view
    }
}
