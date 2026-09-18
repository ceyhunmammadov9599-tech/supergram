package com.supergram.app.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the search-input pipeline (trim -> 300 ms debounce ->
 * distinct -> non-blank) using virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchQueryFlowTest {

    @Test
    fun `rapid query changes coalesce into the last value only`() = runTest {
        val collected = flow {
            emit("a")
            delay(50)
            emit("ab")
            delay(50)
            emit("abc")
            delay(400)
        }.searchDebounce(300).toList()

        assertEquals(listOf("abc"), collected)
    }

    @Test
    fun `blank queries are never emitted`() = runTest {
        val collected = flow {
            emit("")
            delay(400)
            emit("   ")
            delay(400)
        }.searchDebounce(300).toList()

        assertTrue(collected.isEmpty())
    }

    @Test
    fun `queries are trimmed before emission`() = runTest {
        val collected = flow {
            emit("  hello  ")
            delay(400)
        }.searchDebounce(300).toList()

        assertEquals(listOf("hello"), collected)
    }

    @Test
    fun `duplicate consecutive queries are collapsed`() = runTest {
        val collected = flow {
            emit("test")
            delay(400)
            emit("test")
            delay(400)
        }.searchDebounce(300).toList()

        assertEquals(listOf("test"), collected)
    }

    @Test
    fun `slow changes emit every value`() = runTest {
        val collected = flow {
            emit("one")
            delay(400)
            emit("two")
            delay(400)
        }.searchDebounce(300).toList()

        assertEquals(listOf("one", "two"), collected)
    }
}
