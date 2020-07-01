package org.kiot.lexer

import org.kiot.automata.CharClass
import org.kiot.automata.NFABuilder
import org.kiot.util.emptyIntList
import org.kiot.util.intListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class LexerTest {
	@Test
	fun test() {
		val list = emptyIntList()
		Lexer.fromNFA(
			NFABuilder.from(CharClass.letter) to { _ -> list.add(1) },
			NFABuilder.from(CharClass.digit) to { _ -> list.add(2) },
			NFABuilder.from(' ') to { _ -> list.add(3) }
		).lex(" a1ba")
		assertEquals(
			intListOf(3, 1, 2, 1, 1),
			list
		)
	}

	@Test
	fun test2() {
		val list = emptyIntList()
		val lexer = Lexer.fromNFA(
			NFABuilder.from(' ') to { _ -> list.add(1) },
			NFABuilder.from(CharClass.digit).oneOrMore() to { _ -> list.add(2) },
			NFABuilder.from(CharClass.letter).oneOrMore() to { _ -> list.add(3) }
		)
		run {
			list.clear()
			lexer.lex("he is 16 years old")
			assertEquals(
				intListOf(3, 1, 3, 1, 2, 1, 3, 1, 3),
				list
			)
		}
		run {
			list.clear()
			lexer.lex("Ametus is cute 233 ")
			assertEquals(
				intListOf(3, 1, 3, 1, 3, 1, 2, 1),
				list
			)
		}
		run {
			list.clear()
			try {
				lexer.lex("illegal!")
			} catch (e: LexerMismatchException) {
				assertEquals(7, e.startIndex)
				assertEquals(7, e.endIndex)
			}
		}
	}

	@Test
	fun testString() {
		val list = mutableListOf<String>()
		val lexer = Lexer.fromNFA(
			NFABuilder.from(' ') to { _ -> },
			NFABuilder.from(CharClass.letter).oneOrMore() to { s -> list.add(s.string()) }
		)
		lexer.lex("one two three")
		assertEquals(
			mutableListOf("one", "two", "three"),
			list
		)
	}

	@Test
	fun testConflict() {
		assertFails {
			Lexer.fromNFA(
				NFABuilder.from(CharClass.digit) to { _ -> },
				NFABuilder.from(CharClass.any) to { _ -> }
			)
		}
		assertFails {
			Lexer.fromNFA(
				NFABuilder.from("hello") to { _ -> },
				NFABuilder.from(CharClass.letter).oneOrMore() to { _ -> }
			)
		}
	}
}