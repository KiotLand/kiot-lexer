package org.kiot.lexer

import org.kiot.automata.GeneralDFA
import org.kiot.automata.MarkedDFA
import org.kiot.automata.MarkedGeneralDFA
import org.kiot.automata.NFABuilder

interface LexerState {
	val ordinal: Int
}

object EmptyLexerData

class LexerMismatchException(val startIndex: Int, val endIndex: Int) : RuntimeException() {
	override val message: String?
		get() = "Mismatch in [$startIndex, $endIndex]"
}

class MarkedDFABuilder<T> {
	private val pairs = mutableListOf<Pair<NFABuilder, (Lexer.Session<T>.() -> Unit)?>>()

	inline val ignore: (Lexer.Session<T>.() -> Unit)?
		get() = null

	infix fun NFABuilder.then(listener: (Lexer.Session<T>.() -> Unit)?) {
		pairs.add(Pair(this, listener))
	}

	// RegExp
	infix fun String.then(listener: (Lexer.Session<T>.() -> Unit)?) {
		pairs.add(Pair(NFABuilder.fromRegExp(this), listener))
	}

	fun build(minimize: Boolean): MarkedDFA<GeneralDFA, T> {
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
		var (dfa, newMarks) = builder.build().toDFA(marks.asList())
		if (minimize) {
			val pair = dfa.minimize(newMarks)
			dfa = pair.first
			newMarks = pair.second
		}
		newMarks!!
		require(!dfa.isFinal(dfa.beginCell)) { "The DFA built from NFA can match empty string, which is not permitted." }
		return MarkedGeneralDFA(dfa, newMarks)
	}
}

class LexerBuilder<T>(private val minimize: Boolean) {
	private val markedDFAs = mutableListOf<MarkedDFA<*, T>?>()

	val default: Int
		inline get() = 0

	fun state(state: LexerState, block: MarkedDFABuilder<T>.() -> Unit) = state(state.ordinal + 1, block)

	fun state(stateIndex: Int, block: MarkedDFABuilder<T>.() -> Unit) {
		while (markedDFAs.size <= stateIndex) markedDFAs.add(null)
		markedDFAs[stateIndex] = MarkedDFABuilder<T>().apply(block).build(minimize)
	}

	fun build(dataGenerator: () -> T) = Lexer(markedDFAs, dataGenerator)
}

class Lexer<T>(val dfaList: List<MarkedDFA<*, T>?>, val dataGenerator: () -> T) {
	companion object {
		inline fun simple(
			minimize: Boolean = false,
			block: MarkedDFABuilder<EmptyLexerData>.() -> Unit
		): Lexer<EmptyLexerData> =
			Lexer(listOf(MarkedDFABuilder<EmptyLexerData>().apply(block).build(minimize))) { EmptyLexerData }

		inline fun build(
			minimize: Boolean = false,
			block: LexerBuilder<EmptyLexerData>.() -> Unit
		): Lexer<EmptyLexerData> =
			LexerBuilder<EmptyLexerData>(minimize).apply(block).build { EmptyLexerData }

		inline fun <T> simpleWithData(
			noinline dataGenerator: () -> T,
			minimize: Boolean = false,
			block: MarkedDFABuilder<T>.() -> Unit
		): Lexer<T> =
			Lexer(listOf(MarkedDFABuilder<T>().apply(block).build(minimize)), dataGenerator)

		inline fun <T> buildWithData(
			noinline dataGenerator: () -> T,
			minimize: Boolean = false,
			block: LexerBuilder<T>.() -> Unit
		): Lexer<T> =
			LexerBuilder<T>(minimize).apply(block).build(dataGenerator)
	}

	init {
		require(dfaList.isNotEmpty()) { "No DFA exists in list" }
		require(dfaList[0] != null) { "The DFA for initial state mustn't be null" }
	}

	fun lex(chars: CharSequence): T = Session(this, chars, dataGenerator()).apply { lex() }.data

	class Session<T>(private val lexer: Lexer<T>, private val chars: CharSequence, val data: T) {
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
			while (i <= chars.length) {
				var index = if (i == chars.length) -1 else dfa.transitionIndex(x, chars[i])
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
					if (i == chars.length) break
					continue
				}
				val target = dfa.getOut(x, index)
				if (dfa.isFinal(target)) {
					lastIndex = i
					lastNode = x
				}
				x = target
				++i
			}
		}
	}
}