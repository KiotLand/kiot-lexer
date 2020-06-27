package org.kiot.automata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class DFATest {
	@Test
	fun test() {
		NFA.from("1234").toDFA().apply {
			// () --1-> () --2-> () --3--> () --4-> (F)
			assertEquals(5, size)
			assertTrue(match("1234"))
		}
		NFABuilder.branch(
			NFABuilder.from("kotlin"),
			NFABuilder.from("kiot")
		).build().toDFA().apply {
			assertTrue(match("kotlin"))
			assertTrue(match("kiot"))
			assertFalse(match("kot"))
		}
	}
}