package org.kiot.automata

import org.kiot.util.BitSet

/**
 * DFA stands for "Deterministic finite automata".
 *
 * In kiot-lexer, it's implemented through representing states as integers
 * and store their data in several arrays. The index of begin cell is always 0.
 *
 * Pretty different from those in NFA, DFAs in kiot-lexer are mainly built from
 * NFAs so we don't implement modification operations of DFA (I'm curious who would
 * build an DFA manually XD). Obtain a DFA from NFA.
 *
 * @see NFA
 * @see Automata
 */
class DFA internal constructor(
	/**
	 * We store a [List<PlainCharRange>] for each DFA cell so that we can determine which
	 * cell we should transit to. Simplified transition code is:
	 *
	 * ```
	 * val index = charRanges(x).findIndexThatContains(char)
	 * if (index==-1) return
	 * transitTo(outsOf(x)[index])
	 * ```
	 *
	 * The reason why we're not using [CharClass] is that [CharClass] will merge continuous
	 * [PlainCharRange] into one automatically, but in DFA cell continuous [PlainCharRange]s
	 * with different outs exist.
	 */
	private val charRanges: List<List<PlainCharRange>>,
	private val outs: List<List<Int>>,
	/**
	 * Whether a DFA cell is final or not
	 */
	private val finalFlags: BitSet
) {
	val size: Int
		get() = charRanges.size
	val beginCell: Int
		get() = 0

	val indices: IntRange
		get() = 0 until size

	fun charRangesOf(cellIndex: Int) = charRanges[cellIndex]
	fun outsOf(cellIndex: Int) = outs[cellIndex]
	fun isFinal(cellIndex: Int) = finalFlags[cellIndex]

	fun transit(cellIndex: Int, char: Char): Int {
		val ranges = charRanges[cellIndex]
		var l = 0
		var r = ranges.lastIndex
		while (l <= r) {
			val mid = (l + r) ushr 1
			if (char >= ranges[mid].start) {
				if (char <= ranges[mid].end) return outs[cellIndex][mid]
				l = mid + 1
			} else r = mid - 1
		}
		return -1
	}

	fun match(chars: CharSequence, exact: Boolean = true) = match(chars.iterator(), exact)
	fun match(chars: Iterator<Char>, exact: Boolean = true): Boolean {
		if (!chars.hasNext()) return finalFlags[beginCell]
		if ((!exact) && finalFlags[beginCell]) return true
		var x = beginCell
		do {
			x = transit(x, chars.next())
			if (x == -1) return false
			if (!exact && finalFlags[x]) return true
		} while (chars.hasNext())
		return finalFlags[x]
	}
}