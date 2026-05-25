package me.kafuuneko.rpclient.libs.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionExtTest {
    @Test
    fun updateAt_replacesItemWhenIndexIsValid() {
        assertEquals(listOf("a", "x", "c"), listOf("a", "b", "c").updateAt(1, "x"))
    }

    @Test
    fun updateAt_returnsOriginalListWhenIndexIsInvalid() {
        val values = listOf("a", "b")

        assertEquals(values, values.updateAt(3, "x"))
    }

    @Test
    fun removeAtOrSelf_removesItemWhenIndexIsValid() {
        assertEquals(listOf("a", "c"), listOf("a", "b", "c").removeAtOrSelf(1))
    }

    @Test
    fun removeAtOrSelf_returnsOriginalListWhenIndexIsInvalid() {
        val values = listOf("a", "b")

        assertEquals(values, values.removeAtOrSelf(-1))
    }

    @Test
    fun orSingleBlank_returnsBlankInputRowWhenListIsEmpty() {
        assertEquals(listOf(""), emptyList<String>().orSingleBlank())
    }

    @Test
    fun trimmedNotBlank_trimsItemsAndDropsEmptyItems() {
        assertEquals(listOf("a", "b"), listOf(" a ", "", "  ", "b").trimmedNotBlank())
    }

    @Test
    fun toggle_flipsSingleItemMembership() {
        assertEquals(setOf(1L, 3L), setOf(1L, 2L, 3L).toggle(2L))
        assertEquals(setOf(1L, 2L, 3L), setOf(1L, 3L).toggle(2L))
    }

    @Test
    fun toggleAll_removesItemsWhenAllAreSelected() {
        assertEquals(setOf(1L), setOf(1L, 2L, 3L).toggleAll(setOf(2L, 3L)))
    }

    @Test
    fun toggleAll_addsItemsWhenAnyItemIsMissing() {
        assertEquals(setOf(1L, 2L, 3L), setOf(1L, 2L).toggleAll(setOf(2L, 3L)))
    }
}
