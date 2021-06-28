package com.simplemobiletools.filemanager.pro.fragments

import android.content.Context
import android.provider.MediaStore.Files
import android.provider.MediaStore.Files.FileColumns
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.VIEW_TYPE_GRID
import com.simplemobiletools.commons.helpers.VIEW_TYPE_LIST
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.commons.views.MyGridLayoutManager
import com.simplemobiletools.filemanager.pro.R
import com.simplemobiletools.filemanager.pro.activities.MainActivity
import com.simplemobiletools.filemanager.pro.activities.SimpleActivity
import com.simplemobiletools.filemanager.pro.adapters.ItemsAdapter
import com.simplemobiletools.filemanager.pro.extensions.config
import com.simplemobiletools.filemanager.pro.extensions.isPathOnRoot
import com.simplemobiletools.filemanager.pro.helpers.RootHelpers
import com.simplemobiletools.filemanager.pro.interfaces.ItemOperationsListener
import com.simplemobiletools.filemanager.pro.models.ListItem
import kotlinx.android.synthetic.main.recents_fragment.view.*
import java.util.*

class RecentsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet), ItemOperationsListener {
    override fun setupFragment(activity: SimpleActivity) {
        if (this.activity == null) {
            this.activity = activity
            recents_swipe_refresh.setOnRefreshListener { refreshItems() }
        }

        refreshItems()
    }

    override fun refreshItems() {
        ensureBackgroundThread {
            getRecents { recents ->
                recents_swipe_refresh?.isRefreshing = false
                recents_list.beVisibleIf(recents.isNotEmpty())
                recents_placeholder.beVisibleIf(recents.isEmpty())
                addItems(recents, false)

                if (context != null && currentViewType != context!!.config.getFolderViewType(currentPath)) {
                    setupLayoutManager()
                }
            }
        }
    }

    private fun addItems(recents: ArrayList<ListItem>, forceRefresh: Boolean) {
        if (!forceRefresh && recents.hashCode() == (recents_list.adapter as? ItemsAdapter)?.listItems.hashCode()) {
            return
        }

        ItemsAdapter(activity as SimpleActivity, recents, this, recents_list, isPickMultipleIntent, null, recents_swipe_refresh) {
            clickedPath((it as FileDirItem).path)
        }.apply {
            recents_list.adapter = this
        }

        if (context.areSystemAnimationsEnabled) {
            recents_list.scheduleLayoutAnimation()
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int) {
        recents_placeholder.setTextColor(textColor)

        getRecyclerAdapter()?.apply {
            updatePrimaryColor(primaryColor)
            updateTextColor(textColor)
            initDrawables()
        }
    }

    private fun setupLayoutManager() {
        if (context!!.config.getFolderViewType(currentPath) == VIEW_TYPE_GRID) {
            currentViewType = VIEW_TYPE_GRID
            setupGridLayoutManager()
        } else {
            currentViewType = VIEW_TYPE_LIST
            setupListLayoutManager()
        }

        val oldItems = (recents_list.adapter as? ItemsAdapter)?.listItems?.toMutableList() as ArrayList<ListItem>
        recents_list.adapter = null
        addItems(oldItems, true)
    }

    private fun setupGridLayoutManager() {
        val layoutManager = recents_list.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = context?.config?.fileColumnCnt ?: 3

        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (getRecyclerAdapter()?.isASectionTitle(position) == true) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
    }

    private fun setupListLayoutManager() {
        val layoutManager = recents_list.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
    }

    private fun getRecents(callback: (recents: ArrayList<ListItem>) -> Unit) {
        val showHidden = context?.config?.shouldShowHidden ?: return
        val listItems = arrayListOf<ListItem>()

        val uri = Files.getContentUri("external")
        val projection = arrayOf(
            FileColumns.DATA,
            FileColumns.DISPLAY_NAME,
            FileColumns.DATE_MODIFIED,
            FileColumns.SIZE
        )

        val sortOrder = "${FileColumns.DATE_MODIFIED} DESC LIMIT 50"

        context?.queryCursor(uri, projection, sortOrder = sortOrder, showErrors = true) { cursor ->
            val path = cursor.getStringValue(FileColumns.DATA)
            val name = cursor.getStringValue(FileColumns.DISPLAY_NAME) ?: path.getFilenameFromPath()
            val size = cursor.getLongValue(FileColumns.SIZE)
            val modified = cursor.getLongValue(FileColumns.DATE_MODIFIED) * 1000
            val fileDirItem = ListItem(path, name, false, 0, size, modified, false)
            if ((showHidden || !name.startsWith(".")) && activity?.getDoesFilePathExist(path) == true) {
                listItems.add(fileDirItem)
            }
        }

        activity?.runOnUiThread {
            callback(listItems)
        }
    }

    private fun getRecyclerAdapter() = recents_list.adapter as? ItemsAdapter

    override fun toggleFilenameVisibility() {
        getRecyclerAdapter()?.updateDisplayFilenamesInGrid()
    }

    override fun increaseColumnCount() {
        if (currentViewType == VIEW_TYPE_GRID) {
            context?.config?.fileColumnCnt = ++(recents_list.layoutManager as MyGridLayoutManager).spanCount
            columnCountChanged()
        }
    }

    override fun reduceColumnCount() {
        if (currentViewType == VIEW_TYPE_GRID) {
            context?.config?.fileColumnCnt = --(recents_list.layoutManager as MyGridLayoutManager).spanCount
            columnCountChanged()
        }
    }

    private fun columnCountChanged() {
        activity?.invalidateOptionsMenu()
        getRecyclerAdapter()?.apply {
            notifyItemRangeChanged(0, listItems.size)
        }
    }

    override fun setupFontSize() {
        getRecyclerAdapter()?.updateFontSizes()
    }

    override fun setupDateTimeFormat() {
        getRecyclerAdapter()?.updateDateTimeFormat()
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        (activity as MainActivity).pickedPaths(paths)
    }

    override fun deleteFiles(files: ArrayList<FileDirItem>) {
        val firstPath = files.firstOrNull()?.path
        if (firstPath == null || firstPath.isEmpty() || context == null) {
            return
        }

        if (context!!.isPathOnRoot(firstPath)) {
            RootHelpers(activity!!).deleteFiles(files)
        } else {
            (activity as SimpleActivity).deleteFiles(files, false) {
                if (!it) {
                    activity!!.runOnUiThread {
                        activity!!.toast(R.string.unknown_error_occurred)
                    }
                }
            }
        }
    }

    override fun searchQueryChanged(text: String) {}

    override fun finishActMode() {
        getRecyclerAdapter()?.finishActMode()
    }
}
