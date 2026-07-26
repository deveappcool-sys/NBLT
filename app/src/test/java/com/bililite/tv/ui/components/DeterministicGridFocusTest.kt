package com.bililite.tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeterministicGridFocusTest {
    @Test
    fun down_keepsFirstColumn() {
        assertEquals(4, lockedColumnGridTargetIndex(0, 1, 4, 20))
        assertEquals(8, lockedColumnGridTargetIndex(4, 1, 4, 20))
    }

    @Test
    fun down_keepsLastColumn() {
        assertEquals(7, lockedColumnGridTargetIndex(3, 1, 4, 20))
        assertEquals(11, lockedColumnGridTargetIndex(7, 1, 4, 20))
    }

    @Test
    fun up_keepsColumn() {
        assertEquals(1, lockedColumnGridTargetIndex(5, -1, 4, 20))
        assertEquals(3, lockedColumnGridTargetIndex(7, -1, 4, 20))
    }

    @Test
    fun shortLastRow_doesNotDriftIntoAnotherColumn() {
        assertEquals(8, lockedColumnGridTargetIndex(4, 1, 4, 9))
        assertNull(lockedColumnGridTargetIndex(5, 1, 4, 9))
        assertNull(lockedColumnGridTargetIndex(7, 1, 4, 9))
    }

    @Test
    fun gridEdges_stayOnCurrentCard() {
        assertNull(lockedColumnGridTargetIndex(0, -1, 4, 20))
        assertNull(lockedColumnGridTargetIndex(19, 1, 4, 20))
    }
}
