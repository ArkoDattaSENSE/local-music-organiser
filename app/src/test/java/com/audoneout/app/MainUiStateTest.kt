package com.audoneout.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MainUiStateTest {
    @Test
    fun defaultStateStartsWithEmptyLibrary() {
        val state = MainUiState()

        assertEquals(0, state.library.trackCount)
        assertEquals(emptyList<Long>(), state.favorites.map { it.id })
    }
}
