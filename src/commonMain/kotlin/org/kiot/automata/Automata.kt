package org.kiot.automata

/**
 * General automata class.
 *
 * Note: In the whole project, the term of "state" in automata theory is
 *       represented as "cell".
 *
 * @author Mivik
 */
abstract class Automata {
	/**
	 * The amount of cells in this automata.
	 */
	abstract val size: Int

	abstract fun copy(): Automata

	abstract val beginCell: Int

	abstract fun isFinal(cellIndex: Int): Boolean
}

@Suppress("MemberVisibilityCanBePrivate")
class MarksConflictException(val firstMark: Any, val secondMark: Any, val pattern: List<PlainCharRange>? = null) :
	RuntimeException() {
	override val message: String?
		get() = "$firstMark conflicts with $secondMark${pattern.let {
			if (it == null) ""
			else "under this pattern: ${it.joinToString("")}"
		}}"
}

internal fun <T> mergeMark(a: T?, b: T?): T? {
	if ((a == null || b == null) || (a == b)) return a ?: b
	throw MarksConflictException(a, b)
}