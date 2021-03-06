package org.kiot.lexer

import org.kiot.automaton.CharClass
import org.kiot.automaton.MarksConflictException
import org.kiot.automaton.NFA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LexerTest {
	open class IntLexer(data: LexerData, chars: CharSequence) : Lexer<Int>(data, chars) {
		override fun onAction(action: Int) {
			returnValue(action)
		}
	}

	open class StringLexer(data: LexerData, chars: CharSequence) : Lexer<String>(data, chars) {
		override fun onAction(action: Int) {
			returnValue(string())
		}
	}

	@Test
	fun test() {
		val data = LexerData.buildSimple {
			NFA.from(CharClass.letter) action 1
			NFA.from(CharClass.digit) action 2
			NFA.from(' ') action 3
		}

		class TestLexer(chars: CharSequence) : IntLexer(data, chars)
		assertEquals(listOf(3, 1, 2, 1, 1), TestLexer(" a1ba").lexAll())
		assertEquals(listOf(1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2), TestLexer("Daniel13265").lexAll())
		try {
			TestLexer("!").lex()
		} catch (e: LexerMismatchException) {
			assertEquals(0, e.startIndex)
			assertEquals(0, e.endIndex)
		}
	}

	@Test
	fun testString() {
		val data = LexerData.buildSimple {
			NFA.from(' ').ignore()
			NFA.from(CharClass.letter).oneOrMore() action 1
		}

		assertEquals(
			mutableListOf("one", "two", "three"),
			StringLexer(data, "one two three").lexAll()
		)
	}

	@Test
	fun testConflict() {
		assertFailsWith<MarksConflictException> {
			LexerData.buildSimple {
				NFA.from(CharClass.digit) action 1
				NFA.from(CharClass.any) action 2
			}
		}
		assertFailsWith<MarksConflictException> {
			LexerData.buildSimple {
				NFA.from("hello") action 1
				NFA.from(CharClass.letter).oneOrMore() action 2
			}
		}
	}

	@Test
	fun testNonConflict() {
		val data = LexerData.buildSimple {
			options.strict = false
			NFA.from(CharClass.digit) action 1
			NFA.from(CharClass.any) action 2
		}
		assertEquals(1, IntLexer(data, "1").lex())
		assertEquals(2, IntLexer(data, "a").lex())
	}

	@Test
	fun testNormal() {
		data class Word(var name: String, var definition: String)

		val data = LexerData.build {
			options.minimize = true
			state(default) {
				NFA.from(": ") action 1
				NFA.from(CharClass.letter).oneOrMore() action 2
			}
			state(1) {
				NFA.from(CharClass.any).oneOrMore() action 3
			}
		}

		class NormalLexer(chars: CharSequence) : Lexer<Word>(data, chars) {
			val word = Word("", "")

			override fun onAction(action: Int) {
				when (action) {
					1 -> switchState(1)
					2 -> word.name = string()
					3 -> {
						word.definition = string()
						returnValue(word)
					}
				}
			}
		}

		assertEquals(
			NormalLexer("apple: a kind of fruit").lex(),
			Word("apple", "a kind of fruit")
		)
		assertEquals(
			NormalLexer("shocking: !!!").lex(),
			Word("shocking", "!!!")
		)
	}
}