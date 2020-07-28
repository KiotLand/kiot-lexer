package org.kiot.lexer

import org.kiot.automata.ActionMark
import org.kiot.automata.Mark
import org.kiot.automata.MarkedDFA
import org.kiot.automata.MarkedGeneralDFA
import org.kiot.automata.NFA
import org.kiot.automata.PriorityMark
import org.kiot.util.Binarizable
import org.kiot.util.Binarizer
import org.kiot.util.Binary
import org.kiot.util.binarySize

interface LexerState {
	val ordinal: Int
}

class LexerMismatchException(val chars: CharSequence, val startIndex: Int, val endIndex: Int) : RuntimeException() {
	override val message: String?
		get() = "Mismatch in [$startIndex, $endIndex]: ${chars.substring(startIndex, endIndex + 1)}"
}

data class LexerOptions(
	var minimize: Boolean = false,
	var strict: Boolean = true,
	var compress: Boolean = true
)

class MarkedDFABuilder(val options: LexerOptions = LexerOptions()) {
	internal val pairs = mutableListOf<Pair<NFA, ActionMark?>>()

	inline val ignore: ActionMark?
		get() = null

	infix fun NFA.then(action: Int): ActionMark =
		ActionMark(action).also { pairs.add(Pair(this, it)) }

	infix fun NFA.then(mark: ActionMark?) {
		pairs.add(Pair(this, mark))
	}

	// RegExp
	infix fun String.then(action: Int): ActionMark =
		ActionMark(action).also {
			pairs.add(Pair(NFA.fromRegExp(this), it))
			it withName this
		}

	infix fun String.then(mark: ActionMark?) {
		pairs.add(Pair(NFA.fromRegExp(this), mark?.also { it withName this }))
	}

	@Suppress("UNCHECKED_CAST")
	fun build(): MarkedDFA<*> {
		require(pairs.isNotEmpty()) { "DFA used by lexer can not be empty" }
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
		val actions = newMarks.map { IntArray(it.size) { index -> it[index]?.action ?: 0 } }
		val generalDFA = MarkedGeneralDFA(dfa, actions)
		if (options.compress) return generalDFA.compressed()
		return generalDFA
	}
}

class LexerBuilder(val options: LexerOptions = LexerOptions()) {
	private val markedDFAs = mutableListOf<MarkedDFA<*>?>()

	val default: Int
		inline get() = 0

	fun state(state: LexerState, block: MarkedDFABuilder.() -> Unit) = state(state.ordinal + 1, block)

	fun state(stateIndex: Int, block: MarkedDFABuilder.() -> Unit) {
		while (markedDFAs.size <= stateIndex) markedDFAs.add(null)
		markedDFAs[stateIndex] = MarkedDFABuilder(options).apply(block).build()
	}

	fun build() = markedDFAs
}

class LexerData(internal val dfaList: List<MarkedDFA<*>?>) : Binarizable {
	companion object {
		inline fun buildSimple(
			block: MarkedDFABuilder.() -> Unit
		): LexerData = LexerData(listOf(MarkedDFABuilder().apply(block).build()))

		inline fun build(
			block: LexerBuilder.() -> Unit
		): LexerData = LexerData(LexerBuilder().apply(block).build())

		val binarizer = object : Binarizer<LexerData> {
			override fun binarize(bin: Binary, value: LexerData) = bin.run {
				put(value.dfaList.size)
				for (dfa in value.dfaList) {
					if (dfa == null) put(false)
					else {
						put(true)
						put(dfa)
					}
				}
			}

			override fun debinarize(bin: Binary): LexerData =
				LexerData(List(bin.int()) {
					if (bin.boolean()) bin.read<MarkedDFA<*>>()
					else null
				})

			override fun measure(value: LexerData): Int =
				Int.binarySize +
						value.dfaList.sumBy {
							if (it == null) Boolean.binarySize
							else Boolean.binarySize + it.binarySize
						}
		}.also { Binary.register(it) }
	}
}

abstract class Lexer<R>(data: LexerData, val chars: CharSequence) {
	private val dfaList = data.dfaList

	init {
		require(dfaList.isNotEmpty()) { "No DFA exists in list" }
		require(dfaList[0] != null) { "The DFA for initial state mustn't be null" }
	}

	abstract fun onAction(action: Int)

	private var lastMatch = 0
	private var currentDFA = dfaList[0]!!
	protected var index = 0
		private set
	private var result: R? = null

	fun string() = chars.substring(lastMatch, index)

	protected fun switchState(state: LexerState) = switchState(state.ordinal + 1)
	protected fun switchState(stateIndex: Int) {
		dfaList[stateIndex]!!.let {
			if (it == currentDFA) return
			currentDFA = it
		}
	}

	protected fun returnValue(result: R) {
		this.result = result
	}

	fun lex(): R? {
		if (index == chars.length) return null
		var dfa = currentDFA.dfa
		var x = dfa.beginCell
		var lastIndex = -1
		var lastNode = 0
		// note that all the marks lies in final cells, since we only marked end cells in NFAs.
		while (index <= chars.length) {
			var index = if (index == chars.length) -1 else dfa.transitionIndex(x, chars[index])
			if (index == -1) {
				if (lastIndex == -1) throw LexerMismatchException(chars, lastMatch, this.index)
				this.index = lastIndex
				lastIndex = -1
				x = lastNode
				index = dfa.transitionIndex(x, chars[this.index++])
				val action = currentDFA.action(x, index)
				x = dfa.beginCell
				if (action != 0) {
					onAction(action)
					lastMatch = this.index
					dfa = currentDFA.dfa
					if (result != null) return result.also { result = null }
				} else lastMatch = this.index
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
		return null
	}

	fun lexAll(): List<R> {
		val ret = mutableListOf<R>()
		while (true) ret += lex() ?: return ret
	}
}