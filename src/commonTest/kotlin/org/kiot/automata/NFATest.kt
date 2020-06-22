package org.kiot.automata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class NFATest {
	@Test
	fun test() {
		NFA.from("1234").apply {
			assertTrue(match("1234"))
			assertFalse(match("12345"))
			assertTrue(match("12345", exact = false))
		}
		NFA.fromSorted("abd").apply {
			assertTrue(match("a"))
			assertTrue(match("d"))
			assertFalse(match("c"))
		}
	}

	@Test
	fun testChain() {
		NFA.chain(
				NFA.from("123"),
				NFA.from("456")
		).apply {
			// 1->2->3->4->5->6->(Final)
			assertEquals(7, size)
			assertTrue(match("123456"))
		}
		NFA.chain(
				NFA.fromSorted("abc"),
				NFA.from("d")
		).apply {
			assertEquals(3, size)
			assertTrue(match("ad"))
			assertTrue(match("bd"))
			assertFalse(match("d"))
		}
	}

	@Test
	fun testBranch() {
		NFA.branch(
				NFA.from("kotlin"),
				NFA.from("kiot")
		).apply {
			assertEquals(13, size)
			assertTrue(match("kotlin"))
			assertTrue(match("kiot"))
		}
	}

	@Test
	fun testRepeat() {
		NFA.from("a").oneOrMore().apply {
			assertEquals(0, reduce())
			assertFalse(match(""))
			assertTrue(match("a"))
			assertTrue(match("aaa"))
		}
		NFA.from("aa ").unnecessary().apply {
			assertEquals(0, reduce())
			assertTrue(match("aa "))
			assertTrue(match(""))
			assertFalse(match("aa aa "))
		}
		NFA.from("a").any().apply {
			assertEquals(0, reduce())
			assertTrue(match("a"))
			assertTrue(match(""))
			assertTrue(match("aaa"))
		}
	}
}