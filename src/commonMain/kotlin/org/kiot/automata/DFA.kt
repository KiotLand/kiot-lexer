package org.kiot.automata

import org.kiot.util.BitSet
import org.kiot.util.IntList
import org.kiot.util.emptyIntList
import org.kiot.util.intListOf
import org.kiot.util.swap

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
 *
 * @author Mivik
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

	fun transit(cellIndex: Int, char: Char) =
		transitionIndex(cellIndex, char).let {
			if (it == -1) -1
			else outs[cellIndex][it]
		}

	fun transitionIndex(cellIndex: Int, char: Char): Int {
		val ranges = charRanges[cellIndex]
		var l = 0
		var r = ranges.lastIndex
		while (l <= r) {
			val mid = (l + r) ushr 1
			if (char >= ranges[mid].start) {
				if (char <= ranges[mid].end) return mid
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

	internal class TransitionSet : org.kiot.automata.TransitionSet<IntList>() {
		override fun copy(element: IntList): IntList = element.copy()

		override fun IntList.append(other: IntList) {
			addAll(other)
		}
	}

	fun minimize(): DFA {
		var current = mutableListOf<IntList>()
		val group = emptyIntList()
		fun transitionSet(cellIndex: Int): TransitionSet {
			val set = TransitionSet()
			val myRanges = charRanges[cellIndex]
			val myOuts = outs[cellIndex]
			for (i in myRanges.indices) {
				set.add(myRanges[i], intListOf(group[myOuts[i]]))
			}
			set.optimize()
			return set
		}
		run {
			val ordinaryCells = emptyIntList()
			val finalCells = emptyIntList()
			var ordinaryIndex = -1
			var finalIndex = -1
			var tot = 0
			for (cell in indices)
				if (isFinal(cell)) {
					if (finalIndex == -1) {
						finalIndex = tot++
						current.add(finalCells)
					}
					group += finalIndex
					finalCells += cell
				} else {
					if (ordinaryIndex == -1) {
						ordinaryIndex = tot++
						current.add(ordinaryCells)
					}
					group += ordinaryIndex
					ordinaryCells += cell
				}
		}
		while (true) {
			val next = mutableListOf<IntList>()
			for (list in current) {
				val map = mutableMapOf<TransitionSet, IntList>()
				for (cell in list)
					map.getOrPut(transitionSet(cell)) { emptyIntList() } += cell
				for (pair in map) next += pair.value
			}
			if (current.size == next.size) break
			for (i in next.indices) for (cell in next[i]) group[cell] = i
			current = next
		}

		val newSize = current.size
		if (newSize == size) return this // DFAs are immutable, so just return it!

		// Make the begin cell stay in the first place.
		val beginIndex = group[beginCell]
		if (beginIndex != beginCell) {
			for (i in indices) {
				if (group[i] == beginIndex) group[i] = beginCell
				else if (group[i] == beginCell) group[i] = beginIndex
			}
			current.swap(beginCell, beginIndex)
		}

		// Build the minimized DFA.
		val newCharRanges = mutableListOf<List<PlainCharRange>>()
		val newOuts = mutableListOf<List<Int>>()
		val newFinalFlags = BitSet(newSize)
		for (i in 0 until newSize) {
			val myRanges = mutableListOf<PlainCharRange>()
			val myOuts = emptyIntList()
			current[i].first().let {
				val charClass = CharClass.fromSorted(charRanges[it]) // merge them!
				myRanges.addAll(charClass.ranges)
				// all its outs should belong in the same group
				val out = group[outsOf(it)[0]]
				for (range in charClass.ranges) {
					myRanges += range
					myOuts += out
				}
			}
			newCharRanges += myRanges
			newOuts += myOuts
			newFinalFlags[i] = current[i].any { isFinal(it) }
		}
		return DFA(newCharRanges, newOuts, newFinalFlags)
	}
}