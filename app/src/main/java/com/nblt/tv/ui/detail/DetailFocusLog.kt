package com.nblt.tv.ui.detail

import android.util.Log

internal object DetailFocusLog {
    private const val TAG = "BiliDetailFocus"

    fun focusedUpName() {
        if (!ENABLED) return
        Log.d(TAG, "focused=upName, no vertical scroll")
    }

    fun focusedPlayButton() {
        if (!ENABLED) return
        Log.d(TAG, "focused=playButton, no vertical scroll")
    }

    fun focusedSecondaryButton(label: String) {
        if (!ENABLED) return
        Log.d(TAG, "focused=$label, no vertical scroll")
    }

    fun focusedPartItem(index: Int) {
        if (!ENABLED) return
        Log.d(TAG, "focused=partItem index=$index, no vertical scroll")
    }

    fun focusedRelatedItem(index: Int) {
        if (!ENABLED) return
        Log.d(TAG, "focused=relatedItem index=$index, no vertical scroll")
    }

    private const val ENABLED = false
}
