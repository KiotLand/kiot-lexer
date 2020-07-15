package org.kiot.automata

import org.kiot.lexer.Lexer
import org.kiot.lexer.MarkedDFABuilder

sealed class MarkedDFA<D : DFA, T>(val dfa: D) {
	abstract fun transit(session: Lexer.Session<T>, cellIndex: Int, transitionIndex: Int)
}

class MarkedGeneralDFA<T>(dfa: GeneralDFA, private val marks: List<List<MarkedDFABuilder.NamedFunctionMark<T>?>>) :
	MarkedDFA<GeneralDFA, T>(dfa) {
	override fun transit(session: Lexer.Session<T>, cellIndex: Int, transitionIndex: Int) {
		marks[cellIndex][transitionIndex]?.function?.invoke(session)
	}
}

class MarkedCompressedDFA<T>(
	dfa: CompressedDFA,
	private val marks: IntArray,
	val listener: (Int, Lexer.Session<T>) -> Unit
) : MarkedDFA<CompressedDFA, T>(dfa) {
	override fun transit(session: Lexer.Session<T>, cellIndex: Int, transitionIndex: Int) {
		marks[dfa.transition(cellIndex, transitionIndex)].let {
			if (it != 0) listener(it, session)
		}
	}
}