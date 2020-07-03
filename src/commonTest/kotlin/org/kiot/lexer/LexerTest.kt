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
		Lexer.simple {
			NFABuilder.from(CharClass.letter) then { list.add(1) }
			NFABuilder.from(CharClass.digit) then { list.add(2) }
			NFABuilder.from(' ') then { list.add(3) }
		}.lex(" a1ba")
		assertEquals(
			intListOf(3, 1, 2, 1, 1),
			list
		)
	}

	@Test
	fun test2() {
		val list = emptyIntList()
		val lexer = Lexer.simple {
			NFABuilder.from(' ') then { list.add(1) }
			NFABuilder.from(CharClass.digit).oneOrMore() then { list.add(2) }
			NFABuilder.from(CharClass.letter).oneOrMore() then { list.add(3) }
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

	@Test
	fun testString() {
		val list = mutableListOf<String>()
		val lexer = Lexer.simple {
			NFABuilder.from(' ') then null
			NFABuilder.from(CharClass.letter).oneOrMore() then { list.add(string()) }
		}
		lexer.lex("one two three")
		assertEquals(
			mutableListOf("one", "two", "three"),
			list
		)
	}

	@Test
	fun testConflict() {
		assertFails {
			Lexer.simple {
				NFABuilder.from(CharClass.digit) then {}
				NFABuilder.from(CharClass.any) then {}
			}
		}
		assertFails {
			Lexer.simple {
				NFABuilder.from("hello") then {}
				NFABuilder.from(CharClass.letter).oneOrMore() then {}
			}
		}
	}

	@Test
	fun testNormal() {
		data class Word(var name: String, var definition: String)

		val lexer = Lexer.buildWithData({ Word("", "") }, minimize = true) {
			state(default) {
				NFABuilder.from(": ") then { switchState(1) }
				NFABuilder.from(CharClass.letter).oneOrMore() then { data.name = string() }
			}
			state(1) {
				NFABuilder.from(CharClass.any).oneOrMore() then { data.definition = string() }
			}
		}
		assertEquals(
			lexer.lex("apple: a kind of fruit"),
			Word("apple", "a kind of fruit")
		)
		assertEquals(
			lexer.lex("shocking: !!!"),
			Word("shocking", "!!!")
		)
	}

	@Test
	fun testRegExp() {
		data class Data(
			val identifiers: MutableList<String> = mutableListOf(),
			val strings: MutableList<String> = mutableListOf()
		)

		val lexer = Lexer.buildWithData({ Data() }, minimize = true) {
			state(default) {
				"[^\" ]+" then { data.identifiers += string() }
				" " then null
				"\"" then { switchState(1) }
			}
			state(1) {
				"[^\"]+" then { data.strings += string() }
				"\"" then { switchState(default) }
			}
		}
		assertEquals(
			lexer.lex("hello \"world\""),
			Data(mutableListOf("hello"), mutableListOf("world"))
		)
		assertEquals(
			lexer.lex("first \"apple\" second \"peach\""),
			Data(mutableListOf("first", "second"), mutableListOf("apple", "peach"))
		)
	}
}