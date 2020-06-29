package org.kiot.lexer

import org.kiot.automata.CharClass
import org.kiot.automata.NFABuilder
import org.kiot.util.emptyIntList
import org.kiot.util.intListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class LexerTest {
	@Test
	fun test() {
		val list = emptyIntList()
		Lexer.fromNFA(
			NFABuilder.from(CharClass.letter) to 1,
			NFABuilder.from(CharClass.digit) to 2,
			NFABuilder.from(' ') to 3
		) {
			list += it
		}.lex(" a1ba")
		assertEquals(
			intListOf(3, 1, 2, 1, 1),
			list
		)
	}

	@Test
	fun test2() {
		val list = emptyIntList()
		val blank = NFABuilder.from(' ')
		val number = NFABuilder.from(CharClass.digit).oneOrMore()
		val word = NFABuilder.from(CharClass.letter).oneOrMore()
		val lexer = Lexer.fromNFA(
			blank to 1,
			number to 2,
			word to 3
		) {
			list += it
		}
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
}