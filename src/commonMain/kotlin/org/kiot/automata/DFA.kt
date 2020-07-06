package org.kiot.automata

import org.kiot.util.BitSet
import org.kiot.util.IntList
import org.kiot.util.NullableList
import org.kiot.util.emptyIntList
import org.kiot.util.intListOf
import org.kiot.util.swap

sealed class DFA(private val finalFlags: BitSet) {
	val size: Int
		get() = finalFlags.size
	val beginCell: Int
		inline get() = 0

	val indices: IntRange
		inline get() = 0 until size

	fun isFinal(cellIndex: Int) = finalFlags[cellIndex]

	abstract fun transitionIndex(cellIndex: Int, char: Char): Int

	abstract fun getOut(cellIndex: Int, transitionIndex: Int): Int

	open fun transit(cellIndex: Int, char: Char) = getOut(cellIndex, transitionIndex(cellIndex, char))

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
class GeneralDFA internal constructor(
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
	finalFlags: BitSet
) : DFA(finalFlags) {
	fun charRangesOf(cellIndex: Int) = charRanges[cellIndex]
	fun outsOf(cellIndex: Int) = outs[cellIndex]

	override fun transit(cellIndex: Int, char: Char) =
		transitionIndex(cellIndex, char).let {
			if (it == -1) -1
			else outs[cellIndex][it]
		}

	override fun transitionIndex(cellIndex: Int, char: Char): Int {
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

	override fun getOut(cellIndex: Int, transitionIndex: Int): Int = outs[cellIndex][transitionIndex]

	internal class TransitionSet : org.kiot.automata.TransitionSet<IntList>() {
		override fun copy(element: IntList): IntList = element.copy()

		override fun IntList.merge(other: IntList) {
			addAll(other)
		}
	}

	fun minimize() = minimize<Any>(null).first

	fun <T : Any> minimize(marks: List<List<T?>>?): Pair<GeneralDFA, List<List<T?>>?> {
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
		if (newSize == size) return Pair(this, marks) // DFAs are immutable, so just return it!

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
		val newMarks = if (marks == null) null else mutableListOf<List<T?>>()
		for (i in 0 until newSize) {
			val myRanges = mutableListOf<PlainCharRange>()
			val myOuts = emptyIntList()
			if (marks != null) {
				val myMarks = NullableList<T>(current[i].first())
				for (j in current[i].indices) {
					val cell = current[i][j]
					val tmp = marks[cell]
					require(charRanges[cell].size == tmp.size)
					for (k in tmp.indices) myMarks[k] = mergeMark(myMarks[k], tmp[k])
				}
				newMarks!!.add(myMarks)
			}
			current[i].first().let {
				val ranges = charRanges[it]
				val tmp = outs[it]
				myRanges.addAll(ranges)
				for (j in ranges.indices) {
					myRanges += ranges[j]
					myOuts += group[tmp[j]]
				}
			}
			newCharRanges += myRanges
			newOuts += myOuts
			newFinalFlags[i] = current[i].any { isFinal(it) }
		}
		return Pair(GeneralDFA(newCharRanges, newOuts, newFinalFlags), newMarks)
	}
}

class CompressedDFA(
	// charClassIndex(char) = charClassTable[topLevelCharClassTable[char>>8]<<8)|(char&0xFF)]
	private val charClassTable: ShortArray,
	private val topLevelCharClassTable: ByteArray,

	// transitionIndex(cell, char) = transitionIndex[transitionIndexBegin[cell]+charClassIndex(char)]
	private val transitionIndices: IntArray,
	private val transitionIndexBegin: IntArray,

	// transit(cell, char) = transitions[transitionBegin[cell]+transitionIndex(cell, char)]
	private val transitions: IntArray,
	private val transitionBegin: IntArray,

	finalFlags: BitSet
) : DFA(finalFlags) {
	private fun charClassIndex(char: Char) =
		char.toInt().let { charClassTable[(topLevelCharClassTable[it ushr 8].toInt() shl 8) or (it and 0xFF)] }

	override fun transitionIndex(cellIndex: Int, char: Char) =
		transitionIndices[transitionIndexBegin[cellIndex] + charClassIndex(char)]

	fun transition(cellIndex: Int, transitionIndex: Int) = transitionBegin[cellIndex] + transitionIndex

	override fun getOut(cellIndex: Int, transitionIndex: Int) =
		transitions[transition(cellIndex, transitionIndex)]
}