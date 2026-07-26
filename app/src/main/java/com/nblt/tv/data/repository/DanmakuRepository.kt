package com.nblt.tv.data.repository

import android.util.Log
import com.nblt.tv.data.api.BilibiliDanmakuApi
import com.nblt.tv.model.DanmakuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DanmakuRepository(
    private val api: BilibiliDanmakuApi = BilibiliDanmakuApi()
) {
    suspend fun loadDanmaku(cid: Long, maxItems: Int = Int.MAX_VALUE): Result<List<DanmakuItem>> {
        return withContext(Dispatchers.IO) {
            runCatching { api.fetchDanmaku(cid, maxItems) }
                .onFailure { Log.e(TAG, "load fail cid=$cid: ${it.message}", it) }
        }
    }

    private companion object {
        const val TAG = "BiliDanmaku"
    }
}
