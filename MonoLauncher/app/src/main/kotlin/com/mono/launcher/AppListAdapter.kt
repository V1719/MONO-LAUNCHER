package com.mono.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class Row {
    data class Header(val letter: String) : Row()
    data class App(val app: AppInfo) : Row()
}

class AppListAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onMore: (AppInfo, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<Row> = emptyList()

    fun submit(newRows: List<Row>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) =
        if (rows[position] is Row.Header) TYPE_HEADER else TYPE_APP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(inflater.inflate(R.layout.item_header, parent, false))
        } else {
            AppVH(inflater.inflate(R.layout.item_app_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderVH).bind(row.letter)
            is Row.App -> (holder as AppVH).bind(row.app, onClick, onMore)
        }
    }

    override fun getItemCount() = rows.size

    fun positionOf(letter: String) = rows.indexOfFirst { it is Row.Header && it.letter == letter }

    private class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.headerText)
        fun bind(letter: String) { text.text = letter }
    }

    private class AppVH(view: View) : RecyclerView.ViewHolder(view) {
        private val dot: View = view.findViewById(R.id.dot)
        private val name: TextView = view.findViewById(R.id.name)
        private val subtitle: TextView = view.findViewById(R.id.subtitle)
        private val more: View = view.findViewById(R.id.more)

        fun bind(app: AppInfo, onClick: (AppInfo) -> Unit, onMore: (AppInfo, View) -> Unit) {
            name.text = app.label
            dot.background?.setTint(colorForName(app.label))

            val peek = NotificationTracker.peek(app.packageName)
            if (peek != null) {
                val extras = peek.notification.extras
                val t = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                val body = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
                subtitle.text = listOfNotNull(t, body).joinToString(" · ").ifBlank { null }
                subtitle.visibility = if (subtitle.text.isNullOrBlank()) View.GONE else View.VISIBLE
            } else {
                subtitle.visibility = View.GONE
            }

            itemView.setOnClickListener { onClick(app) }
            more.setOnClickListener { onMore(app, more) }
        }

        private fun colorForName(label: String): Int {
            var h = 0
            for (c in label) h = (h * 31 + c.code) % 360
            return android.graphics.Color.HSVToColor(floatArrayOf(h.toFloat(), 0.42f, 0.75f))
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1
    }
}
