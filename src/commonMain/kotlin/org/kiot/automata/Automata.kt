package org.kiot.automata

/**
 * General automata class.
 *
 * Note: In the whole project, the term of "state" in automata theory is
 *       represented as "cell".
 */
abstract class Automata {
	/**
	 * The amount of cells in this automata.
	 */
	abstract val size: Int

	abstract fun copy(): Automata
}