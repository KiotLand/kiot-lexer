package org.kiot.util

import kotlin.test.Test
import kotlin.test.assertEquals

internal class PrimitiveListTest {
	@Test
	fun test() {
		assertEquals(
				intListOf(1, 3),
				intListOf(1, 2, 3, 4).apply { removeIf { it % 2 == 0 } }
		)
		assertEquals(
				intListOf(0, 3),
				intListOf(0, 1, 2, 3).apply { removeRange(1, 3) }
		)
		assertEquals(
				booleanListOf(true, true, false),
				booleanListOf(false, true, true, false).apply { removeAt(0) }
		)
	}
}