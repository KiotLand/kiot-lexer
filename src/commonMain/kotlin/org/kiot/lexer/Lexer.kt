package org.kiot.lexer

import org.kiot.automata.GeneralDFA
import org.kiot.automata.Mark
import org.kiot.automata.MarkedDFA
import org.kiot.automata.MarkedGeneralDFA
import org.kiot.automata.NFA

interface LexerState {
	val ordinal: Int
}

object EmptyLexerData

class LexerMismatchException(val startIndex: Int, val endIndex: Int) : RuntimeException() {
	override val message: String?
		get() = "Mismatch in [$startIndex, $endIndex]"
}

data class LexerOptions(var minimize: Boolean = false, var strict: Boolean = true)

class MarkedDFABuilder<T>(val options: LexerOptions = LexerOptions()) {
	class NamedFunctionMark<T>(val function: Lexer.Session<T>.() -> Unit, var name: String = function.toString()) :
		Mark {
		override fun merge(other: Mark): Mark = this

		override fun canMerge(other: Mark): Boolean {
			if (other !is NamedFunctionMark<*>) return false
			return function == other.function && name == other.name
		}

		override fun toString(): String = "FunctionMark($name)"

		infix fun withName(name: String) {
			this.name = name
		}
	}

	class PriorityMark<T : Mark>(val priority: Int, val mark: T) : Mark {
		override fun merge(other: Mark): Mark {
			other as PriorityMark<*>
			return if (priority < other.priority) this
			else other
		}

		override fun canMerge(other: Mark): Boolean = other is PriorityMark<*>

		override fun toString(): String = "PriorityMark($priority, $mark)"
	}

	private val pairs = mutableListOf<Pair<NFA, NamedFunctionMark<T>?>>()

	inline val ignore: NamedFunctionMark<T>?
		get() = null

	infix fun NFA.then(listener: Lexer.Session<T>.() -> Unit) {
		pairs.add(Pair(this, NamedFunctionMark(listener)))
	}

	infix fun NFA.then(mark: NamedFunctionMark<T>?) {
		pairs.add(Pair(this, mark))
	}

	// RegExp
	infix fun String.then(listener: Lexer.Session<T>.() -> Unit): NamedFunctionMark<T> =
		NamedFunctionMark(listener).also { pairs.add(Pair(NFA.fromRegExp(this), it)) }.also { it withName this }

	infix fun String.then(mark: NamedFunctionMark<T>?) {
		pairs.add(Pair(NFA.fromRegExp(this), mark?.also { it withName this }))
	}

	@Suppress("UNCHECKED_CAST")
	fun build(): MarkedDFA<GeneralDFA, T> {
		require(pairs.isNotEmpty()) { "DFA used for lexer can not be empty" }
		val builder = NFA()
		val nfa = builder.nfa
		val newBegin = nfa.appendDummyCell()
		builder.extend(newBegin)
		val beginOuts = nfa.outsOf(newBegin)
		val newEnd = nfa.appendDummyCell()
		val marks = arrayOfNulls<Mark>(pairs.sumBy { it.first.size } + 2)
		val strict = options.strict
		for (index in pairs.indices) {
			val pair = pairs[index]
			beginOuts += pair.first.beginCell + nfa.size
			builder.include(pair.first)
			nfa.link(builder.endCell, newEnd)
			marks[builder.endCell] =
				if (strict) pair.second else pair.second?.let { PriorityMark(index, it) }
		}
		builder.makeEnd(newEnd)
		var (dfa, newMarks) = builder.static().toDFA(marks.asList())
		if (options.minimize) {
			val pair = dfa.minimize(newMarks)
			dfa = pair.first
			newMarks = pair.second
		}
		newMarks!!
		require(!dfa.isFinal(dfa.beginCell)) { "The DFA built from NFA can match empty string, which is not permitted." }
		return if (strict) MarkedGeneralDFA(dfa, newMarks as List<List<NamedFunctionMark<T>?>>)
		else MarkedGeneralDFA(
			dfa,
			newMarks.map { it.map { each -> (each as PriorityMark<NamedFunctionMark<T>>).mark } })
	}
}

class LexerBuilder<T>(val options: LexerOptions = LexerOptions()) {
	private val markedDFAs = mutableListOf<MarkedDFA<*, T>?>()

	val default: Int
		inline get() = 0

	fun state(state: LexerState, block: MarkedDFABuilder<T>.() -> Unit) = state(state.ordinal + 1, block)

	fun state(stateIndex: Int, block: MarkedDFABuilder<T>.() -> Unit) {
		while (markedDFAs.size <= stateIndex) markedDFAs.add(null)
		markedDFAs[stateIndex] = MarkedDFABuilder<T>(options).apply(block).build()
	}

	fun build(dataGenerator: () -> T) = Lexer(markedDFAs, dataGenerator)
}

class Lexer<T>(val dfaList: List<MarkedDFA<*, T>?>, val dataGenerator: () -> T) {
	companion object {
		inline fun simple(
			block: MarkedDFABuilder<EmptyLexerData>.() -> Unit
		): Lexer<EmptyLexerData> =
			Lexer(listOf(MarkedDFABuilder<EmptyLexerData>().apply(block).build())) { EmptyLexerData }

		inline fun build(
			block: LexerBuilder<EmptyLexerData>.() -> Unit
		): Lexer<EmptyLexerData> =
			LexerBuilder<EmptyLexerData>().apply(block).build { EmptyLexerData }

		inline fun <T> simpleWithData(
			noinline dataGenerator: () -> T,
			block: MarkedDFABuilder<T>.() -> Unit
		): Lexer<T> =
			Lexer(listOf(MarkedDFABuilder<T>().apply(block).build()), dataGenerator)

		inline fun <T> buildWithData(
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

	class Session<T>(private val lexer: Lexer<T>, private val chars: CharSequence, val data: T) {
		private var lastMatch = 0
		private var currentDFA = lexer.dfaList[0]!!
		var index = 0
			private set

		fun string() = chars.substring(lastMatch, index)

		fun switchState(state: LexerState) = switchState(state.ordinal + 1)
		fun switchState(stateIndex: Int) {
			lexer.dfaList[stateIndex]!!.let {
				if (it == currentDFA) return
				currentDFA = it
			}
		}

		fun lex() {
			if (index == chars.length) return
			var dfa = currentDFA.dfa
			var x = dfa.beginCell
			var lastIndex = -1
			var lastNode = 0
			// note that all the marks lies in final cells, since we only marked end cells in NFAs.
			while (index <= chars.length) {
				var index = if (index == chars.length) -1 else dfa.transitionIndex(x, chars[index])
				if (index == -1) {
					if (lastIndex == -1) throw LexerMismatchException(lastMatch, this.index)
					this.index = lastIndex
					lastIndex = -1
					x = lastNode
					index = dfa.transitionIndex(x, chars[this.index++])
					currentDFA.transit(this, x, index)
					dfa = currentDFA.dfa
					lastMatch = this.index
					x = dfa.beginCell
					if (this.index == chars.length) break
					continue
				}
				val target = dfa.getOut(x, index)
				if (dfa.isFinal(target)) {
					lastIndex = this.index
					lastNode = x
				}
				x = target
				++this.index
			}
		}
	}
}