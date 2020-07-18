package org.kiot.automata

import org.kiot.util.Binarizable
import org.kiot.util.Binarizer
import org.kiot.util.Binary
import org.kiot.util.BitSet
import org.kiot.util.CircularIntQueue
import org.kiot.util.IntList
import org.kiot.util.MutablePair
import org.kiot.util.binarySize
import org.kiot.util.booleanListOf
import org.kiot.util.intListOf
import kotlin.native.concurrent.ThreadLocal

/**
 * NFA stands for "Nondeterministic finite automata".
 *
 * In kiot-lexer, it's implemented through representing states as integers
 * and store their data in several arrays. The index of begin cell is stored
 * in NFA.
 *
 * @see DFA
 * @see Automata
 *
 * @author Mivik
 */
class StaticNFA(
	/**
	 * In kiot-lexer, transitions are stored in cells themselves. Each
	 * cell has a [CharClass]. Let A be a cell, and its [CharClass]
	 * be C. If we are now at cell A, and we received a new char
	 * that is contained in C, so we'll extend the [CellList] with
	 * A's [outs], otherwise we'll do nothing.
	 *
	 * Specially, if a cell's [CharClass] is empty, we'll treat it as
	 * a dummy cell which does not accept any char and only transits
	 * to all its outs when being stepped in.
	 */
	private val charClasses: MutableList<CharClass> = mutableListOf(),
	private val outs: MutableList<IntList> = mutableListOf() // Mentioned above
) : Automata(), Binarizable {
	companion object {
		fun from(vararg chars: Char) = NFA.from(*chars).static()
		fun fromSorted(vararg chars: Char) = NFA.fromSorted(*chars).static()
		fun fromSorted(chars: String) = NFA.fromSorted(chars).static()
		fun from(charClass: CharClass) = NFA.from(charClass).static()

		fun from(chars: CharSequence) = NFA.from(chars).static()
		fun from(chars: Iterator<Char>) = NFA.from(chars).static()

		fun fromRegExp(regexp: String) = NFA.fromRegExp(regexp).static()

		val binarizer = object : Binarizer<StaticNFA> {
			override fun binarize(bin: Binary, value: StaticNFA) = value.run {
				bin.put(size)
				for (charClass in charClasses) bin.put(charClass, CharClass.binarizer)
				for (out in outs) bin.put(out, IntList.binarizer)
				bin.put(beginCell)
			}

			override fun debinarize(bin: Binary): StaticNFA {
				val size = bin.int()
				return StaticNFA(
					MutableList(size) { bin.read(CharClass.binarizer) },
					MutableList(size) { bin.read(IntList.binarizer) }
				).also { it.beginCell = bin.int() }
			}

			override fun measure(value: StaticNFA): Int =
				Binary.measureList(value.charClasses) + Binary.measureList(value.outs) + Int.binarySize
		}.also { Binary.register(it) }
	}

	fun link(from: Int, to: Int) {
		outs[from].clear()
		outs[from].add(to)
	}

	override val size: Int
		get() = charClasses.size
	override var beginCell = finalCell
	val finalCell: Int
		inline get() = -1

	val indices: IntRange
		inline get() = 0 until size

	override fun copy(): StaticNFA =
		StaticNFA(
			charClasses.toMutableList(),
			outs.mapTo(mutableListOf()) { it.copy() }
		).also { it.beginCell = beginCell }

	private fun <T : Mark> markedPutInto(cellIndex: Int, list: CellList, marks: List<T?>): T? {
		if (isFinal(cellIndex) || !isDummy(cellIndex)) {
			list.add(cellIndex)
			return null
		}
		var mark = marks[cellIndex]
		for (i in outs[cellIndex]) mark = mergeMark(mark, markedPutInto(i, list, marks))
		return mark
	}

	private fun putInto(cellIndex: Int, list: CellList) {
		if (isFinal(cellIndex) || !isDummy(cellIndex)) {
			list.add(cellIndex)
			return
		}
		for (i in outs[cellIndex]) putInto(i, list)
	}

	private fun transit(cellIndex: Int, char: Char, list: CellList) {
		if (isFinal(cellIndex)) return
		// When the cell is dummy, its CharClass should be empty and this following check will be satisfied.
		if (char !in charClasses[cellIndex]) return
		for (i in outs[cellIndex]) putInto(i, list)
	}

	fun match(chars: CharSequence, exact: Boolean = true) = match(chars.iterator(), exact)
	fun match(chars: Iterator<Char>, exact: Boolean = true) =
		CellList(this).apply { putInto(beginCell, this) }.match(chars, exact)

	operator fun plusAssign(other: StaticNFA) {
		val offset = size
		charClasses += other.charClasses
		for (i in 0 until other.size)
			outs += other.outs[i].mapTo(intListOf()) { if (isFinal(it)) it else (it + offset) }
	}

	fun isDummy(cellIndex: Int) = charClasses[cellIndex].isEmpty()
	fun charClassOf(cellIndex: Int) = charClasses[cellIndex]
	fun outsOf(cellIndex: Int) = outs[cellIndex]
	override fun isFinal(cellIndex: Int) = cellIndex == -1

	fun setCharClass(cellIndex: Int, charClass: CharClass) {
		this.charClasses[cellIndex] = charClass
	}

	fun setOuts(cellIndex: Int, outs: IntList) {
		this.outs[cellIndex] = outs
	}

	fun appendCell(charClass: CharClass, outs: IntList = intListOf()): Int {
		this.charClasses += charClass
		this.outs += outs
		return size - 1
	}

	fun appendDummyCell(outs: IntList = intListOf()): Int = appendCell(CharClass.empty, outs)

	fun clear() {
		charClasses.clear()
		outs.clear()
		beginCell = finalCell
	}

	internal fun clearRange(from: Int, to: Int) {
		charClasses.subList(from, to).clear()
		outs.subList(from, to).clear()
	}

	/**
	 * A list of NFA cells.
	 */
	class CellList(
		private val nfa: StaticNFA,
		private val bitset: BitSet = BitSet(nfa.size),
		private val list: IntList = intListOf(),
		private var finalCount: Int = 0
	) {
		companion object {
			// I know it's pretty dirty.. It's to avoid using try-catch!
			@ThreadLocal
			internal var lastRange: PlainCharRange = PlainCharRange.empty
		}

		val size: Int
			get() = list.size

		val hasFinal: Boolean
			get() = finalCount != 0

		fun add(element: Int) {
			if (nfa.isFinal(element)) {
				++finalCount
				return
			}
			if (bitset[element]) return
			bitset.set(element)
			list.add(element)
		}

		fun addAll(elements: IntList) {
			for (element in elements) add(element)
		}

		fun remove(element: Int) {
			if (nfa.isFinal(element)) {
				--finalCount
				return
			}
			if (!bitset[element]) return
			bitset.clear(element)
			list.remove(element)
		}

		fun contains(element: Int): Boolean = bitset[element]

		fun isEmpty(): Boolean = size == 0
		fun isNotEmpty(): Boolean = size != 0

		operator fun iterator() = Iterator()

		fun clear() {
			bitset.clear()
			list.clear()
			finalCount = 0
		}

		fun copy(): CellList = CellList(nfa, bitset.copy(), list.copy(), finalCount)

		fun match(chars: CharSequence, exact: Boolean = true) = match(chars.iterator(), exact)
		fun match(chars: kotlin.collections.Iterator<Char>, exact: Boolean = true): Boolean {
			if (!chars.hasNext()) return hasFinal
			if ((!exact) && hasFinal) return true
			var listA = copy()
			var listB = CellList(nfa)
			do {
				val char = chars.next()
				for (cell in listA) {
					nfa.transit(cell, char, listB)
					if (listB.hasFinal && !exact) return true
				}
				val tmp = listA
				listA = listB
				listB = tmp
				listB.clear()
			} while (chars.hasNext())
			return listA.hasFinal
		}

		override fun hashCode(): Int = bitset.hashCode() * 31 + (if (finalCount == 0) 0 else 1)
		override fun equals(other: Any?): Boolean =
			if (other is CellList) hasFinal == other.hasFinal && bitset == other.bitset
			else false

		inner class Iterator : kotlin.collections.Iterator<Int> {
			private var index = 0

			override fun hasNext(): Boolean = index != list.size

			override fun next(): Int = list[index++]
		}

		internal fun <T : Mark> transitionSet(): TransitionSet<T> {
			val set = TransitionSet<T>()
			for (cell in this) {
				val ranges = nfa.charClasses[cell].ranges
				val list = CellList(nfa)
				for (out in nfa.outs[cell]) nfa.putInto(out, list)
				for (range in ranges)
					set.add(range, MutablePair(list, null))
			}
			set.optimize()
			return set
		}

		internal fun <T : Mark> transitionSet(marks: List<T?>): TransitionSet<T> {
			val set = TransitionSet<T>()
			for (cell in this) {
				val ranges = nfa.charClasses[cell].ranges
				val list = CellList(nfa)
				var mark = marks[cell]
				val outs = nfa.outs[cell]
				for (index in outs.indices) {
					lastRange = ranges[index]
					mark = mergeMark(mark, nfa.markedPutInto(outs[index], list, marks))
				}
				for (range in ranges) set.add(range, MutablePair(list, mark))
			}
			set.optimize()
			return set
		}

		internal class TransitionSet<T : Mark> :
			org.kiot.automata.TransitionSet<MutablePair<CellList, T?>>() {
			override fun copy(element: MutablePair<CellList, T?>) =
				MutablePair(element.first.copy(), element.second)

			override fun MutablePair<CellList, T?>.merge(other: MutablePair<CellList, T?>) {
				second = mergeMark(second, other.second)
				first.addAll(other.first.list)
			}
		}

		override fun toString(): String = "[${list.joinToString(", ")}]"
	}

	fun toDFA(): GeneralDFA = toDFA<Nothing>(null).first

	/**
	 * Convert a NFA into DFA using Subset Construction.
	 */
	fun <T : Mark> toDFA(marks: List<T?>?): Pair<GeneralDFA, List<List<T?>>?> {
		require(marks == null || marks.size == size)

		val charRanges = mutableListOf<MutableList<PlainCharRange>>()
		val outs = mutableListOf<MutableList<Int>>()
		val finalFlags = booleanListOf()
		val queue = CircularIntQueue(size)

		// TODO maybe use mutableMapOf(LinkedHashMap) here?
		val cellMap = hashMapOf<CellList, Int>()
		val sets = mutableListOf<CellList.TransitionSet<T>>()
		val transitionMarks = if (marks == null) null else mutableListOf<MutableList<T?>>()
		val transitionPath = if (marks == null) null else mutableListOf<Pair<PlainCharRange, Int>?>()
		fun indexOf(list: CellList, data: Pair<PlainCharRange, Int>?): Int =
			cellMap[list] ?: run {
				val index = charRanges.size
				cellMap[list] = index
				queue.push(index)
				transitionMarks?.add(mutableListOf())
				transitionPath?.add(data)
				sets.add(if (marks == null) list.transitionSet() else list.transitionSet(marks))
				charRanges.add(mutableListOf())
				outs.add(mutableListOf())
				finalFlags += list.hasFinal
				index
			}

		try {
			CellList(this).apply {
				putInto(beginCell, this)
				indexOf(this, null)
			}
			while (queue.isNotEmpty()) {
				val x = queue.pop()
				val set = sets[x]
				val myCharRanges = charRanges[x]
				val myOuts = outs[x]
				val newMarks = transitionMarks?.get(x)
				for (pair in set) {
					myCharRanges.add(pair.first)
					myOuts.add(indexOf(pair.second.first, Pair(pair.first, x)))
					newMarks?.add(pair.second.second)
				}
			}
		} catch (e: MarksConflictException) {
			val list = mutableListOf<PlainCharRange>()
			list.add(CellList.lastRange)
			transitionPath!!
			var x = transitionPath.lastIndex
			while (x != 0) {
				val pair = transitionPath[x] ?: break
				list.add(pair.first)
				x = pair.second
			}
			throw MarksConflictException(e.firstMark, e.secondMark, list.asReversed())
		}
		return Pair(
			GeneralDFA(charRanges, outs, finalFlags.toBitSet()),
			transitionMarks
		)
	}
}