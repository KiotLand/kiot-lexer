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

internal fun <T> mergeMark(a: T?, b: T?): T? {
	require((a == null || b == null) || (a == b)) { "Marks conflict: $a and $b" }
	return a ?: b
}