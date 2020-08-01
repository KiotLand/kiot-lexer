package org.kiot.automaton

/**
 * General automata class.
 *
 * Note: In the whole project, the term of "state" in automata theory is
 *       represented as "cell".
 *
 * @author Mivik
 */
abstract class Automaton {
	/**
	 * The amount of cells in this automata.
	 */
	abstract val size: Int

	abstract fun copy(): Automaton

	abstract val beginCell: Int

	abstract fun isFinal(cellIndex: Int): Boolean
}