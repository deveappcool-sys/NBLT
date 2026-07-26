package com.nblt.tv.model

/**
 * Stable identity for Compose list keys and focus restoration.
 * PGC entries retain ep/season/media identity even after detail loading fills aid/bvid.
 */
val VideoItem.stableContentKey: String
    get() {
        val isPgc = contentType == VideoContentType.PGC || epId > 0L || seasonId > 0L || mediaId > 0L
        return when {
            isPgc && epId > 0L -> "pgc:ep:$epId"
            isPgc && seasonId > 0L -> "pgc:season:$seasonId"
            isPgc && mediaId > 0L -> "pgc:media:$mediaId"
            bvid.isNotBlank() -> "ugc:bvid:$bvid"
            aid > 0L -> "ugc:aid:$aid"
            cid > 0L -> "ugc:cid:$cid"
            else -> "fallback:${title.hashCode()}:${coverUrl.hashCode()}"
        }
    }
