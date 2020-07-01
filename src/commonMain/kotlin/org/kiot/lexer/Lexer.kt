package org.kiot.lexer

import org.kiot.automata.DFA
import org.kiot.automata.NFABuilder

class Lexer private constructor(val dfa: DFA, val listener: (Session, Int, Int) -> Unit) {
	companion object {
		fun fromNFA(vararg pairs: Pair<NFABuilder, (Session) -> Unit>): Lexer {
			require(pairs.isNotEmpty())
			val builder = NFABuilder()
			val nfa = builder.nfa
			val newBegin = nfa.appendDummyCell()
			builder.extend(newBegin)
			val beginOuts = nfa.outsOf(newBegin)
			val newEnd = nfa.appendDummyCell()
			val marks = arrayOfNulls<(Session) -> Unit>(pairs.sumBy { it.first.size } + 2)
			for (pair in pairs) {
				beginOuts += pair.first.beginCell + nfa.size
				builder.include(pair.first)
				nfa.link(builder.endCell, newEnd)
				marks[builder.endCell] = pair.second
			}
			builder.makeEnd(newEnd)
			val (dfa, newMarks) = builder.build().toDFA(marks.asList())
			newMarks!!
			require(!dfa.isFinal(dfa.beginCell)) { "The DFA built from NFA can match empty string, which is not permitted." }
			return Lexer(dfa) { session, cell, index -> newMarks[cell][index]?.invoke(session) }
		}
	}

	fun lex(chars: CharSequence) = Session(chars).lex()

	inner class Session(val chars: CharSequence) {
		private var lastIndex = -1
		private var lastNode = 0
		private var lastMatch = 0
		private var i = 0

		fun string() = chars.substring(lastMatch, i)

		fun lex() {
			if (i == chars.length) return
			var x = dfa.beginCell
			// note that all the marks lies in final cells, since we only marked end cells in NFAs.
			while (i < chars.length) {
				var index = dfa.transitionIndex(x, chars[i])
				if (index == -1) {
					if (lastIndex == -1) throw LexerMismatchException(lastMatch, i)
					i = lastIndex
					lastIndex = -1
					x = lastNode
					index = dfa.transitionIndex(x, chars[i++])
					listener(this, x, index)
					lastMatch = i
					x = dfa.beginCell
					continue
				}
				val target = dfa.outsOf(x)[index]
				if (dfa.isFinal(target)) {
					lastIndex = i
					lastNode = x
				}
				x = target
				++i
			}
			if (lastIndex == -1) throw LexerMismatchException(lastMatch, i)
			listener(this, lastNode, dfa.transitionIndex(lastNode, chars[lastIndex]))
		}
	}
}