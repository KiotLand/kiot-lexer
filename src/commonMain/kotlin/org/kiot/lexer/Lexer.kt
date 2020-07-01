package org.kiot.lexer

import org.kiot.automata.DFA
import org.kiot.automata.NFABuilder
import org.kiot.util.nullableListOf

interface LexerState {
	val ordinal: Int
}

interface LexerData

object EmptyLexerData : LexerData

class MarkedDFA<T : LexerData>(val dfa: DFA, private val marks: List<List<(Lexer.Session<T>.() -> Unit)?>>) {
	fun transit(session: Lexer.Session<T>, cellIndex: Int, transitIndex: Int) {
		marks[cellIndex][transitIndex]?.invoke(session)
	}
}

class MarkedDFABuilder<T : LexerData> {
	private val pairs = mutableListOf<Pair<NFABuilder, (Lexer.Session<T>.() -> Unit)?>>()

	infix fun NFABuilder.then(listener: (Lexer.Session<T>.() -> Unit)?) {
		pairs.add(Pair(this, listener))
	}

	fun build(): MarkedDFA<T> {
		require(pairs.isNotEmpty()) { "DFA used for lexer can not be empty" }
		val builder = NFABuilder()
		val nfa = builder.nfa
		val newBegin = nfa.appendDummyCell()
		builder.extend(newBegin)
		val beginOuts = nfa.outsOf(newBegin)
		val newEnd = nfa.appendDummyCell()
		val marks = arrayOfNulls<Lexer.Session<T>.() -> Unit>(pairs.sumBy { it.first.size } + 2)
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

class LexerBuilder<T : LexerData> {
	private val markedDFAs = nullableListOf<MarkedDFA<T>>()

	val default: Int
		get() = 0

	fun state(state: LexerState, block: MarkedDFABuilder<T>.() -> Unit) = state(state.ordinal + 1, block)

	fun state(stateIndex: Int, block: MarkedDFABuilder<T>.() -> Unit) {
		markedDFAs.resize(stateIndex + 1)
		markedDFAs[stateIndex] = MarkedDFABuilder<T>().apply(block).build()
	}

	fun build(dataGenerator: () -> T) = Lexer(markedDFAs, dataGenerator)
}

class Lexer<T : LexerData>(val dfaList: List<MarkedDFA<T>?>, val dataGenerator: () -> T) {
	companion object {
		inline fun simple(block: MarkedDFABuilder<EmptyLexerData>.() -> Unit): Lexer<EmptyLexerData> =
			Lexer(listOf(MarkedDFABuilder<EmptyLexerData>().apply(block).build())) { EmptyLexerData }

		inline fun build(block: LexerBuilder<EmptyLexerData>.() -> Unit): Lexer<EmptyLexerData> =
			LexerBuilder<EmptyLexerData>().apply(block).build() { EmptyLexerData }

		inline fun <T : LexerData> simpleWithData(
			noinline dataGenerator: () -> T,
			block: MarkedDFABuilder<T>.() -> Unit
		): Lexer<T> =
			Lexer(listOf(MarkedDFABuilder<T>().apply(block).build()), dataGenerator)

		inline fun <T : LexerData> buildWithData(
			noinline dataGenerator: () -> T,
			block: LexerBuilder<T>.() -> Unit
		): Lexer<T> =
			LexerBuilder<T>().apply(block).build(dataGenerator)
	}

	init {
		require(dfaList.isNotEmpty()) { "No DFA exists in list" }
		require(dfaList[0] != null) { "The DFA for initial state mustn't be null" }
	}

	fun lex(chars: CharSequence): T = Session(this, chars, dataGenerator()).apply { lex() }.data

	class Session<T : LexerData>(private val lexer: Lexer<T>, private val chars: CharSequence, val data: T) {
		private var lastMatch = 0
		private var currentDFA = lexer.dfaList[0]!!
		private var i = 0

		fun string() = chars.substring(lastMatch, i)

		fun switchState(state: LexerState) = switchState(state.ordinal + 1)
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