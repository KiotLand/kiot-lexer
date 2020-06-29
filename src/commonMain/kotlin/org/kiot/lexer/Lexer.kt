package org.kiot.lexer

import org.kiot.automata.DFA
import org.kiot.automata.NFABuilder
import org.kiot.util.emptyIntList

class Lexer private constructor(val dfa: DFA, val listener: (Int, Int) -> Unit) {
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
			require(!dfa.isFinal(dfa.beginCell)) { "The DFA built from NFA can match empty string, which is not permitted." }
			return Lexer(dfa) { cell, index -> listener(newMarks[cell][index]) }
		}
	}

	// greedy
	fun lex(chars: CharSequence) {
		if (chars.isEmpty()) return
		var x = dfa.beginCell
		var i = 0
		var lastIndex = -1
		var lastNode = 0
		var lastMatch = -1
		// note that all the marks lies in final cells, since we only marked end cells in NFAs.
		while (i < chars.length) {
			var index = dfa.transitionIndex(x, chars[i])
			if (index == -1) {
				if (lastIndex == -1) throw LexerMismatchException(lastMatch+1, i)
				i = lastIndex
				lastMatch = lastIndex
				lastIndex = -1
				x = lastNode
				index = dfa.transitionIndex(x, chars[i++])
				listener(x, index)
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
		if (lastIndex == -1) throw LexerMismatchException(lastMatch+1, i)
		listener(lastNode, dfa.transitionIndex(lastNode, chars[lastIndex]))
	}
}