package org.kiot.lexer

import org.kiot.automata.DFA
import org.kiot.automata.NFABuilder
import org.kiot.util.nullableListOf

interface KiotState {
	val ordinal: Int
}

class MarkedDFA(val dfa: DFA, private val marks: List<List<(Lexer.Session.() -> Unit)?>>) {
	fun transit(session: Lexer.Session, cellIndex: Int, transitIndex: Int) {
		marks[cellIndex][transitIndex]?.invoke(session)
	}
}

class MarkedDFABuilder {
	private val pairs = mutableListOf<Pair<NFABuilder, (Lexer.Session.() -> Unit)?>>()

	infix fun NFABuilder.then(listener: (Lexer.Session.() -> Unit)?) {
		pairs.add(Pair(this, listener))
	}

	fun build(): MarkedDFA {
		require(pairs.isNotEmpty()) { "DFA used for lexer can not be empty" }
		val builder = NFABuilder()
		val nfa = builder.nfa
		val newBegin = nfa.appendDummyCell()
		builder.extend(newBegin)
		val beginOuts = nfa.outsOf(newBegin)
		val newEnd = nfa.appendDummyCell()
		val marks = arrayOfNulls<Lexer.Session.() -> Unit>(pairs.sumBy { it.first.size } + 2)
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
		return MarkedDFA(dfa, newMarks)
	}
}

class LexerBuilder {
	private val markedDFAs = nullableListOf<MarkedDFA>()

	val default: Int
		get() = 0

	fun state(state: KiotState, block: MarkedDFABuilder.() -> Unit) = state(state.ordinal + 1, block)

	fun state(stateIndex: Int, block: MarkedDFABuilder.() -> Unit) {
		markedDFAs.resize(stateIndex + 1)
		markedDFAs[stateIndex] = MarkedDFABuilder().apply(block).build()
	}

	fun build() = Lexer(markedDFAs)
}

class Lexer(val dfaList: List<MarkedDFA?>) {
	companion object {
		inline fun simple(block: MarkedDFABuilder.() -> Unit): Lexer =
			Lexer(listOf(MarkedDFABuilder().apply(block).build()))

		inline fun build(block: LexerBuilder.() -> Unit): Lexer = LexerBuilder().apply(block).build()
	}

	init {
		require(dfaList.isNotEmpty()) { "No DFA exists in list" }
		require(dfaList[0] != null) { "The DFA for initial state mustn't be null" }
	}

	fun lex(chars: CharSequence) = Session(this, chars).lex()

	class Session(private val lexer: Lexer, private val chars: CharSequence) {
		private var lastMatch = 0
		private var currentDFA = lexer.dfaList[0]!!
		private var i = 0

		fun string() = chars.substring(lastMatch, i)

		fun switchState(state: KiotState) = switchState(state.ordinal + 1)
		fun switchState(stateIndex: Int) {
			lexer.dfaList[stateIndex]!!.let {
				if (it == currentDFA) return // mark here
				currentDFA = it
			}
		}

		fun lex() {
			if (i == chars.length) return
			var dfa = currentDFA.dfa
			var x = dfa.beginCell
			var lastIndex = -1
			var lastNode = 0
			// note that all the marks lies in final cells, since we only marked end cells in NFAs.
			while (i < chars.length) {
				var index = dfa.transitionIndex(x, chars[i])
				if (index == -1) {
					if (lastIndex == -1) throw LexerMismatchException(lastMatch, i)
					i = lastIndex
					lastIndex = -1
					x = lastNode
					index = dfa.transitionIndex(x, chars[i++])
					currentDFA.transit(this, x, index)
					dfa = currentDFA.dfa
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
			currentDFA.transit(this, lastNode, dfa.transitionIndex(lastNode, chars[lastIndex]))
		}
	}
}