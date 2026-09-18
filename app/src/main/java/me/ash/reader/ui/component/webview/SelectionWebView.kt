package me.ash.reader.ui.component.webview

import android.content.Context
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView

/** Extend Android's existing selection menu without replacing selection handles/copy/share. */
class SelectionWebView(context: Context) : WebView(context) {
    var explanationsEnabled = false
    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        if (!explanationsEnabled) return super.startActionMode(callback, type)
        return super.startActionMode(object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val created = callback.onCreateActionMode(mode, menu)
                if (created) {
                    menu.add(0, 7801, 1, "解释").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    menu.add(0, 7802, 2, "提问…").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    menu.add(0, 7803, 3, "翻译这段").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                }
                return created
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = callback.onPrepareActionMode(mode, menu)
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId !in 7801..7803) return callback.onActionItemClicked(mode, item)
                evaluateJavascript("window.ReadYouNotes && window.ReadYouNotes.select(${item.itemId - 7801})") { mode.finish() }
                return true
            }
            override fun onDestroyActionMode(mode: ActionMode) = callback.onDestroyActionMode(mode)
            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                if (callback is ActionMode.Callback2) callback.onGetContentRect(mode, view, outRect)
                else super.onGetContentRect(mode, view, outRect)
            }
        }, type)
    }
}
