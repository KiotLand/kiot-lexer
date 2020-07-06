package org.kiot.automata

import org.kiot.lexer.Lexer

sealed class MarkedDFA<D : DFA, T>(val dfa: D) {
	abstract fun transit(session: Lexer.Session<T>, cellIndex: Int, transitionIndex: Int)
}

class MarkedGeneralDFA<T>(dfa: GeneralDFA, private val marks: List<List<(Lexer.Session<T>.() -> Unit)?>>) :
	MarkedDFA<GeneralDFA, T>(dfa) {
	override fun transit(session: Lexer.Session<T>, cellIndex: Int, transitionIndex: Int) {
		marks[cellIndex][transitionIndex]?.invoke(session)
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