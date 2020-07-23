package org.kiot.automata

import org.kiot.lexer.Lexer

interface Mark {
	fun merge(other: Mark): Mark

	fun canMerge(other: Mark): Boolean
}

@Suppress("MemberVisibilityCanBePrivate")
class MarksConflictException(val firstMark: Mark, val secondMark: Mark, val pattern: List<PlainCharRange>? = null) :
	RuntimeException() {
	override val message: String?
		get() = "$firstMark conflicts with $secondMark${pattern.let {
			if (it == null) ""
			else " under this pattern: ${it.joinToString("")}"
		}}"
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Mark> mergeMark(a: T?, b: T?): T? {
	if (a == null || b == null) return a ?: b
	if (a.canMerge(b)) return a.merge(b) as T?
	throw MarksConflictException(a, b)
}

class FunctionMark<T>(val function: Lexer.Session<T>.() -> Unit, var name: String = function.toString()) : Mark {
	override fun merge(other: Mark): Mark = this

	override fun canMerge(other: Mark): Boolean {
		if (other !is FunctionMark<*>) return false
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

sealed class MarkedDFA<D : DFA, T>(val dfa: D) {
	abstract fun transit(session: Lexer.Session<T>, cellIndex: Int, transitionIndex: Int)
}

class MarkedGeneralDFA<T>(dfa: GeneralDFA, private val marks: List<List<FunctionMark<T>?>>) :
	MarkedDFA<GeneralDFA, T>(dfa) {
	override fun transit(session: Lexer.Session<T>, cellIndex: Int, transitionIndex: Int) {
		marks[cellIndex][transitionIndex]?.function?.invoke(session)
	}

	fun compressed(): MarkedCompressedDFA<T> {
		val newDFA = dfa.compressed()
		val newMarks = arrayOfNulls<FunctionMark<T>>(newDFA.transitionSize)
		var tot = 0
		for (cell in dfa.indices) {
			val cur = marks[cell]
			for (i in cur.indices) newMarks[tot++] = cur[i]
		}
		return MarkedCompressedDFA(newDFA, newMarks)
	}
}

class MarkedCompressedDFA<T>(
	dfa: CompressedDFA,
	private val marks: Array<FunctionMark<T>?>
) : MarkedDFA<CompressedDFA, T>(dfa) {
	override fun transit(session: Lexer.Session<T>, cellIndex: Int, transitionIndex: Int) {
		marks[dfa.transition(cellIndex, transitionIndex)]?.function?.invoke(session)
	}
}