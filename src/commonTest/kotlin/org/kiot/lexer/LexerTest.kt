package org.kiot.lexer

import org.kiot.automata.CharClass
import org.kiot.automata.NFABuilder
import kotlin.test.Test

class LexerTest {
	@Test
	fun test() {
		Lexer.fromNFA(
			NFABuilder.from(CharClass.letter) to 1,
			NFABuilder.from(CharClass.digit) to 2,
			NFABuilder.from(' ') to 3
		) {
			println(it)
		}.lex("a")
	}
}