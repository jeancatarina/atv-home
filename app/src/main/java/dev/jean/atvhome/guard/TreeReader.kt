package dev.jean.atvhome.guard

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

object TreeReader {
    // Every node read makes the app's own UI thread answer: a big tree must not cost the film a frame
    private const val MAX_NODES = 400
    private const val BUDGET_MS = 12L

    /**
     * Every visible text of the window in front. [ids]: the view ids that name an ad or a skip
     * button. Apps that draw with their own engine yield an empty list.
     */
    fun read(root: AccessibilityNodeInfo?, width: Int, height: Int, ids: List<String>): Reading? {
        root ?: return null
        val labels = ArrayList<Label>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        val box = Rect()
        val deadline = SystemClock.uptimeMillis() + BUDGET_MS
        var seen = 0
        while (queue.isNotEmpty() && seen++ < MAX_NODES && SystemClock.uptimeMillis() < deadline) {
            val node = queue.removeFirst()
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            if (!node.isVisibleToUser) continue
            val text = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
            val id = node.viewIdResourceName
            // A node without text only matters when its id names an ad or a skip button
            if (text.isEmpty() && (id == null || ids.none { it in id })) continue
            node.getBoundsInScreen(box)
            labels += Label(
                text, box.exactCenterX() / width, box.exactCenterY() / height,
                box.width().toFloat() / width, box.height().toFloat() / height, id,
            )
        }
        return Reading(Source.TREE, labels = labels)
    }
}
