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
			else "under this pattern: ${it.joinToString("")}"
		}}"
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Mark> mergeMark(a: T?, b: T?): T? {
	if (a == null || b == null) return a ?: b
	if (a.canMerge(b)) return a.merge(b) as T?
	throw MarksConflictException(a, b)
}