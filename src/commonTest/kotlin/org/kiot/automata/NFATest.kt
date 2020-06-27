package org.kiot.automata

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class NFATest {
	companion object {
		/**
		 * A NFA that matches decimal representations of integers that can be divided by 3.
		 */
		fun buildThree(): NFA {
			val `0369` = NFABuilder.fromSorted("0369").any()
			val `147` = NFABuilder.fromSorted("147")
			val `258` = NFABuilder.fromSorted("258")
			return NFABuilder()
				.append(`0369`)
				.append(
					NFABuilder.branch(
						NFABuilder()
							.appendBranch(
								NFABuilder()
									.append(`147`)
									.append(`0369`),
								NFABuilder()
									.append(`258`)
									.append(`0369`)
									.append(`258`)
									.append(`0369`)
							).append(
								NFABuilder()
									.append(`147`)
									.append(`0369`)
									.append(`258`)
									.append(`0369`)
									.any()
							).appendBranch(
								NFABuilder()
									.append(`258`)
									.append(`0369`),
								NFABuilder()
									.append(`147`)
									.append(`0369`)
									.append(`147`)
									.append(`0369`)
							),
						NFABuilder()
							.append(`258`)
							.append(`0369`)
							.append(`147`)
							.append(`0369`)
					).any()
				).build()
		}
	}

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
	fun testAppend() {
		NFABuilder.from("123")
			.append("456")
			.append("789").build().apply {
				assertEquals(9, size)
				assertTrue(match("123456789"))
			}
		NFABuilder.from("123").append(NFABuilder.from("456")).build().apply {
			assertEquals(6, size)
			assertTrue(match("123456"))
		}
	}

	@Test
	fun testChain() {
		NFABuilder.chain(
			NFABuilder.from("123"),
			NFABuilder.from("456")
		).build().apply {
			// 1->2->3->4->5->6->(Final)
			assertEquals(6, size)
			assertTrue(match("123456"))
		}
		NFABuilder.chain(
			NFABuilder.fromSorted("abc"),
			NFABuilder.from("d")
		).build().apply {
			assertEquals(2, size)
			assertTrue(match("ad"))
			assertTrue(match("bd"))
			assertFalse(match("d"))
		}
	}

	@Test
	fun testBranch() {
		NFABuilder.branch(
			NFABuilder.from("kotlin"),
			NFABuilder.from("kiot")
		).build().apply {
			// 6(kotlin)+4(kiot)+2(begin and end) = 12
			assertEquals(12, size)
			assertTrue(match("kotlin"))
			assertTrue(match("kiot"))
		}
	}

	@Test
	fun testRepeat() {
		NFABuilder.from("a").oneOrMore().apply {
			assertEquals(0, reduce())
		}.build().apply {
			assertFalse(match(""))
			assertTrue(match("a"))
			assertTrue(match("aaa"))
		}
		NFABuilder.from("aa ").unnecessary().apply {
			assertEquals(0, reduce())
		}.build().apply {
			assertTrue(match("aa "))
			assertTrue(match(""))
			assertFalse(match("aa aa "))
		}
		NFABuilder.from("a").any().apply {
			assertEquals(0, reduce())
		}.build().apply {
			assertTrue(match("a"))
			assertTrue(match(""))
			assertTrue(match("aaa"))
		}
	}

	@Test
	fun testThree() {
		val nfa = buildThree()
		repeat(200) {
			val number = Random.nextInt(0, 2000) * 3
			assertTrue(nfa.match(number.toString()))
		}
	}
}