package org.kiot.automata

import org.kiot.util.Binarizable
import org.kiot.util.Binarizer
import org.kiot.util.Binary
import org.kiot.util.BitSet
import org.kiot.util.IntList
import org.kiot.util.emptyIntList
import org.kiot.util.intListOf
import org.kiot.util.measureSize
import org.kiot.util.swap

sealed class DFA(protected val finalFlags: BitSet) : Binarizable {
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
	companion object : Binarizer<GeneralDFA>() {
		init {
			Binary.register(this)
		}

		override fun binarize(bin: Binary, value: GeneralDFA) = bin.run {
			put(value.size)
			for (ranges in value.charRanges) putList(ranges)
			for (outs in value.outs) putList(outs, Binary.binarizer())
			write(value.finalFlags, BitSet)
		}

		override fun debinarize(bin: Binary): GeneralDFA {
			val size = bin.int()
			return GeneralDFA(
				Array(size) { bin.readList(PlainCharRange) }.asList(),
				Array(size) { bin.readList(Binary.binarizer<Int>()) }.asList(),
				bin.read(BitSet)
			)
		}

		override fun dynamicMeasure(value: GeneralDFA): Int =
			4 + value.charRanges.sumBy { 4 + it.size * 4 } + value.outs.sumBy { 4 + it.size * 4 } + value.finalFlags.measureSize(
				BitSet
			)
	}

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
				val myMarks = arrayOfNulls<Any>(current[i].first())
				for (j in current[i].indices) {
					val cell = current[i][j]
					val tmp = marks[cell]
					require(charRanges[cell].size == tmp.size)
					for (k in tmp.indices) myMarks[k] = mergeMark(myMarks[k], tmp[k])
				}
				@Suppress("UNCHECKED_CAST")
				newMarks!!.add(myMarks as List<T?>)
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

	private class CharClassMapKey(val array: ShortArray, val index: Int) {
		override fun equals(other: Any?): Boolean =
			if (other is CharClassMapKey) run {
				for (i in 0 until 256) if (array[index + i] != other.array[other.index + i]) return false
				return true
			} else false

		override fun hashCode(): Int {
			var ret = 0
			for (i in index until index + 256) ret = (ret * 31) + array[i]
			return ret
		}
	}

	private class TransitionMapKey(val array: IntArray) {
		override fun equals(other: Any?): Boolean =
			if (other is TransitionMapKey) array.contentEquals(other.array)
			else false

		override fun hashCode(): Int = array.contentHashCode()
	}

	fun compressed(): CompressedDFA {
		val set = object : org.kiot.automata.TransitionSet<Unit>() {
			override fun copy(element: Unit) {}

			override fun Unit.merge(other: Unit) {}
		}
		for (cell in indices)
			for (range in charRangesOf(cell))
				set.add(range, Unit)
		val ranges = set.ranges
		val array = ShortArray(65536)
		val topLevelCharClassTable = ByteArray(256)
		val charClassTable: ShortArray
		var charClassCount: Int
		val charClassMap = emptyIntList()
		val transitionIndices: IntArray
		val transitionIndexBegin = IntArray(size)
		val transitions: IntArray
		val transitionBegin = IntArray(size)
		run {
			run {
				val units = set.targets
				var index = -1
				var tot = 0.toShort()
				var cur = 0.toShort()
				for (char in Char.MIN_VALUE..Char.MAX_VALUE) {
					if (index != ranges.size - 1 && char >= ranges[index + 1].start) {
						++index
						cur = if (units[index] == null) -1 else {
							charClassMap += index
							tot++
						}
					}
					array[char - Char.MIN_VALUE] = cur
				}
				charClassCount = tot.toInt()
			}
			val map = mutableMapOf<CharClassMapKey, Int>()
			for (i in 0 until 256)
				topLevelCharClassTable[i] = map.getOrPut(CharClassMapKey(array, i shl 8)) { map.size }.toByte()
			charClassTable = ShortArray(map.size shl 8)
			for (pair in map)
				array.copyInto(charClassTable, pair.value shl 8, pair.key.index, pair.key.index + 256)
		}
		run {
			val map = mutableMapOf<TransitionMapKey, Int>()
			for (cell in indices) {
				val transition = IntArray(charClassCount)
				for (i in 0 until charClassCount)
					transition[i] = transitionIndex(cell, ranges[charClassMap[i]].start)
				transitionIndexBegin[cell] = map.getOrPut(TransitionMapKey(transition)) { map.size } * charClassCount
			}
			transitionIndices = IntArray(map.size * charClassCount)
			for (pair in map)
				pair.key.array.copyInto(transitionIndices, pair.value * charClassCount)
		}
		run {
			transitions = IntArray(indices.sumBy { outsOf(it).size })
			var tot = 0
			for (cell in indices) {
				transitionBegin[cell] = tot
				val outs = outsOf(cell)
				for (i in outs.indices) transitions[tot + i] = outs[i]
				tot += outs.size
			}
		}
		return CompressedDFA(
			charClassTable,
			topLevelCharClassTable,
			transitionIndices,
			transitionIndexBegin,
			transitions,
			transitionBegin,
			finalFlags
		)
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
) : DFA(finalFlags), Binarizable {
	companion object : Binarizer<CompressedDFA>() {
		init {
			Binary.register(this)
		}

		override fun binarize(bin: Binary, value: CompressedDFA) = bin.run {
			put(value.charClassTable)
			put(value.topLevelCharClassTable)
			put(value.transitionIndices)
			put(value.transitionIndexBegin)
			put(value.transitions)
			put(value.transitionBegin)
			write(value.finalFlags, BitSet)
		}

		override fun debinarize(bin: Binary): CompressedDFA =
			CompressedDFA(
				bin.shortArray(),
				bin.byteArray(),
				bin.intArray(),
				bin.intArray(),
				bin.intArray(),
				bin.intArray(),
				bin.read(BitSet)
			)

		override fun dynamicMeasure(value: CompressedDFA): Int {
			return Binary.measure(value.charClassTable) +
					Binary.measure(value.topLevelCharClassTable) +
					Binary.measure(value.transitionIndices) +
					Binary.measure(value.transitionIndexBegin) +
					Binary.measure(value.transitions) +
					Binary.measure(value.transitionBegin) +
					value.finalFlags.measureSize(BitSet)
		}
	}

	private fun charClassIndex(char: Char) =
		char.toInt().let { charClassTable[(topLevelCharClassTable[it ushr 8].toInt() shl 8) or (it and 0xFF)] }

	override fun transitionIndex(cellIndex: Int, char: Char) =
		charClassIndex(char).let {
			if (it == (-1).toShort()) -1
			else transitionIndices[transitionIndexBegin[cellIndex] + it]
		}

	fun transition(cellIndex: Int, transitionIndex: Int) = transitionBegin[cellIndex] + transitionIndex

	override fun getOut(cellIndex: Int, transitionIndex: Int) =
		transitions[transition(cellIndex, transitionIndex)]
}