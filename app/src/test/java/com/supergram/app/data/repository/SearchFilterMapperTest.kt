package com.supergram.app.data.repository

import com.supergram.app.domain.model.SearchFilter
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the domain SearchFilter -> TDLib SearchMessagesFilter
 * mapping (data-layer only; null = no filter).
 */
class SearchFilterMapperTest {

    private fun SearchFilter.toTd(): TdApi.SearchMessagesFilter? = when (this) {
        SearchFilter.ALL -> null
        SearchFilter.PHOTOS -> TdApi.SearchMessagesFilterPhoto()
        SearchFilter.DOCUMENTS -> TdApi.SearchMessagesFilterDocument()
        SearchFilter.LINKS -> TdApi.SearchMessagesFilterUrl()
    }

    @Test
    fun `ALL maps to a null TDLib filter`() {
        assertNull(SearchFilter.ALL.toTd())
    }

    @Test
    fun `PHOTOS maps to SearchMessagesFilterPhoto`() {
        assertTrue(SearchFilter.PHOTOS.toTd() is TdApi.SearchMessagesFilterPhoto)
    }

    @Test
    fun `DOCUMENTS maps to SearchMessagesFilterDocument`() {
        assertTrue(SearchFilter.DOCUMENTS.toTd() is TdApi.SearchMessagesFilterDocument)
    }

    @Test
    fun `LINKS maps to SearchMessagesFilterUrl`() {
        assertTrue(SearchFilter.LINKS.toTd() is TdApi.SearchMessagesFilterUrl)
    }
}
