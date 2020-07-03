package org.kiot.automata

import kotlin.random.Random
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
		NFABuilder.from("0").oneOrMore().build().toDFA().apply {
			assertTrue(match("0000"))
			assertTrue(match("0"))
			assertFalse(match(""))
			assertFalse(match("1"))
		}
		NFABuilder.from("0").any().build().toDFA().apply {
			assertTrue(match("0000"))
			assertTrue(match(""))
			assertFalse(match("1"))
		}
		NFABuilder.branch(
			NFABuilder.from("a "),
			NFABuilder.from("b ")
		).any().build().toDFA().apply {
			assertTrue(match("a b "))
			assertTrue(match("b b a "))
			assertTrue(match(""))
			assertFalse(match("a"))
		}
	}

	@Test
	fun testThree() {
		val dfa = NFATest.buildThree().toDFA()
		repeat(200) {
			val number = Random.nextInt(0, 2000) * 3
			assertTrue(dfa.match(number.toString()))
			assertFalse(dfa.match((number+1).toString()))
			assertFalse(dfa.match((number+2).toString()))
		}
	}

	@Test
	fun testMinimize() {
		val dfa = NFATest.buildThree().toDFA().minimize()
		assertEquals(3, dfa.size)
		repeat(200) {
			val number = Random.nextInt(0, 2000) * 3
			assertTrue(dfa.match(number.toString()))
		}
	}
}