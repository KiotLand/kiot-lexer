package org.kiot.lexer

import org.kiot.automata.DFA
import org.kiot.automata.NFABuilder

class Lexer(val dfa: DFA, val listener: (Int, Int) -> Unit) {
	companion object {
		fun fromNFA(vararg pairs: Pair<NFABuilder, Int>, listener: (Int) -> Unit): Lexer {
			require(pairs.isNotEmpty())
			val builder = NFABuilder()
			val nfa = builder.nfa
			val newBegin = nfa.appendDummyCell()
			builder.extend(newBegin)
			val beginOuts = nfa.outsOf(newBegin)
			val newEnd = nfa.appendDummyCell()
			val marks = IntArray(pairs.sumBy { it.first.size } + 2)
			for (pair in pairs) {
				beginOuts += pair.first.beginCell + nfa.size
				builder.include(pair.first)
				nfa.link(builder.endCell, newEnd)
				marks[builder.endCell] = pair.second
			}
			builder.makeEnd(newEnd)
			val (dfa, newMarks) = builder.build().toDFA(marks)
			newMarks!!
			return Lexer(dfa) { cell, index -> listener(newMarks[cell][index]) }
		}
	}

	fun lex(chars: CharSequence) = lex(chars.iterator())
	fun lex(chars: Iterator<Char>) {
		if (!chars.hasNext()) return
		var x = dfa.beginCell
		do {
			val index = dfa.transitionIndex(x, chars.next())
			if (index == -1) return
			listener(x, index)
			x = dfa.outsOf(x)[index]
		} while (chars.hasNext())
	}
}