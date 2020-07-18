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
		fun buildThree(): StaticNFA {
			val `0369` = NFA.fromSorted("0369").any()
			val `147` = NFA.fromSorted("147")
			val `258` = NFA.fromSorted("258")
			return NFA()
				.append(`0369`)
				.append(
					NFA.branch(
						NFA()
							.appendBranch(
								NFA()
									.append(`147`)
									.append(`0369`),
								NFA()
									.append(`258`)
									.append(`0369`)
									.append(`258`)
									.append(`0369`)
							).append(
								NFA()
									.append(`147`)
									.append(`0369`)
									.append(`258`)
									.append(`0369`)
									.any()
							).appendBranch(
								NFA()
									.append(`258`)
									.append(`0369`),
								NFA()
									.append(`147`)
									.append(`0369`)
									.append(`147`)
									.append(`0369`)
							),
						NFA()
							.append(`258`)
							.append(`0369`)
							.append(`147`)
							.append(`0369`)
					).any()
				).static()
		}
	}

	@Test
	fun test() {
		StaticNFA.from("1234").apply {
			assertTrue(match("1234"))
			assertFalse(match("12345"))
			assertTrue(match("12345", exact = false))
		}
		StaticNFA.fromSorted("abd").apply {
			assertTrue(match("a"))
			assertTrue(match("d"))
			assertFalse(match("c"))
		}
	}

	@Test
	fun testAppend() {
		NFA.from("123")
			.append("456")
			.append("789").static().apply {
				assertEquals(9, size)
				assertTrue(match("123456789"))
			}
		NFA.from("123").append(NFA.from("456")).static().apply {
			assertEquals(6, size)
			assertTrue(match("123456"))
		}
	}

	@Test
	fun testChain() {
		NFA.chain(
			NFA.from("123"),
			NFA.from("456")
		).static().apply {
			// 1->2->3->4->5->6->(Final)
			assertEquals(6, size)
			assertTrue(match("123456"))
		}
		NFA.chain(
			NFA.fromSorted("abc"),
			NFA.from("d")
		).static().apply {
			assertEquals(2, size)
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
		).static().apply {
			// 6(kotlin)+4(kiot)+2(begin and end) = 12
			assertEquals(12, size)
			assertTrue(match("kotlin"))
			assertTrue(match("kiot"))
		}
	}

	@Test
	fun testRepeat() {
		NFA.from("a").oneOrMore().apply {
			assertEquals(0, reduce())
		}.static().apply {
			assertFalse(match(""))
			assertTrue(match("a"))
			assertTrue(match("aaa"))
		}
		NFA.from("aa ").unnecessary().apply {
			assertEquals(0, reduce())
		}.static().apply {
			assertTrue(match("aa "))
			assertTrue(match(""))
			assertFalse(match("aa aa "))
		}
		NFA.from("a").any().apply {
			assertEquals(0, reduce())
		}.static().apply {
			assertTrue(match("a"))
			assertTrue(match(""))
			assertTrue(match("aaa"))
		}
		NFA.from("a ").repeat(1, 3).static().apply {
			assertTrue(match("a "))
			assertTrue(match("a a "))
			assertTrue(match("a a a "))
			assertFalse(match(""))
			assertFalse(match("a a a a "))
		}
		NFA.from("a ").repeatAtLeast(2).static().apply {
			assertFalse(match("a "))
			assertTrue(match("a a "))
			assertTrue(match("a a a "))
			assertTrue(match("a a a a a "))
		}
	}

	@Test
	fun testThree() {
		val nfa = buildThree()
		repeat(200) {
			val number = Random.nextInt(0, 2000) * 3
			assertTrue(nfa.match(number.toString()))
			assertFalse(nfa.match((number + 1).toString()))
			assertFalse(nfa.match((number + 2).toString()))
		}
	}

	@Test
	fun testRegExp() {
		StaticNFA.fromRegExp("(simple string)").apply {
			assertTrue(match("simple string"))
		}
		StaticNFA.fromRegExp("[0-9]").apply {
			for (i in '0'..'9') assertTrue(match(i.toString()))
			for (i in 'a'..'z') assertFalse(match(i.toString()))
		}
		StaticNFA.fromRegExp("[0-9a-z]").apply {
			for (i in '0'..'9') assertTrue(match(i.toString()))
			for (i in 'a'..'z') assertTrue(match(i.toString()))
		}
		StaticNFA.fromRegExp("[\\w][\\W][\\d][\\s]").apply {
			assertTrue(match("a_3 "))
			assertFalse(match("ab3 "))
		}
		StaticNFA.fromRegExp("[^0-9]+").apply {
			assertTrue(match("kiot and kotlin"))
			assertFalse(match("number 0"))
		}
		StaticNFA.fromRegExp("(cat|dog)").apply {
			assertTrue(match("cat"))
			assertTrue(match("dog"))
			assertFalse(match("cog")) // wtf?
		}
		StaticNFA.fromRegExp("((cat|dog|frog) )*").apply {
			assertTrue(match(""))
			assertTrue(match("dog cat "))
			assertTrue(match("cat cat dog "))
			assertTrue(match("cat cat dog frog "))
			assertFalse(match("cat")) // no tailing space
		}
		StaticNFA.fromRegExp("\\d+").apply {
			assertTrue(match("123"))
			assertFalse(match(""))
		}
		StaticNFA.fromRegExp("\\d{1,4}").apply {
			assertTrue(match("1234"))
			assertTrue(match("1926"))
			assertFalse(match(""))
			assertFalse(match("12345"))
		}
		StaticNFA.fromRegExp("\\w{3,}").apply {
			assertTrue(match("cat"))
			assertTrue(match("kotlin"))
			assertFalse(match("do"))
			assertFalse(match("a"))
		}
		StaticNFA.fromRegExp("[0369]*(([147][0369]*|[258][0369]*[258][0369]*)([147][0369]*[258][0369]*)*([258][0369]*|[147][0369]*[147][0369]*)|[258][0369]*[147][0369]*)*")
			.apply {
				repeat(200) {
					val number = Random.nextInt(0, 2000) * 3
					assertTrue(match(number.toString()))
					assertFalse(match((number + 1).toString()))
					assertFalse(match((number + 2).toString()))
				}
			}
		// Matches negative floating-point number
		StaticNFA.fromRegExp("-(([0-9]+\\.[0-9]*[1-9][0-9]*)|([0-9]*[1-9][0-9]*\\.[0-9]+)|([0-9]*[1-9][0-9]*))").apply {
			repeat(200) {
				val number = Random.nextDouble(0.1, 1.0)
				assertFalse(match(number.toString()))
				assertTrue(match((-number).toString()))
			}
		}
		// Matches email address
		StaticNFA.fromRegExp("\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*").apply {
			assertTrue(match("mivik@qq.com"))
			assertTrue(match("anonymous@safemail.com"))
		}
	}

	@Test
	fun testComposedRegExp() {
		run {
			val number = "\\d+".regexp()
			val word = "\\w+".regexp()
			(RegExp + "((" + number + "|" + word + ") )+").build().apply {
				assertTrue(match("i have a dream "))
				assertTrue(match("42 is a mysterious number "))
			}
		}
		run {
			val capitalizedWord = "[A-Z]\\w+".regexp()
			val word = "\\w+".regexp()
			val number = "\\d+".regexp()
			val sentence = (RegExp + capitalizedWord + "( (" + word + "|" + number + "))+").build()
			assertTrue(sentence.match("We can deal with numbers like 1926"))
			assertFalse(sentence.match("not capitalized"))
		}
	}
}