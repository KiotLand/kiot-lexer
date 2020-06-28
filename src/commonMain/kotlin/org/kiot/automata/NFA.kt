package org.kiot.automata

import org.kiot.util.BitSet
import org.kiot.util.CircularIntQueue
import org.kiot.util.IntList
import org.kiot.util.MutablePair
import org.kiot.util.emptyBooleanList
import org.kiot.util.emptyIntList

/**
 * NFA stands for "Nondeterministic finite automata".
 *
 * In kiot-lexer, it's implemented through representing states as integers
 * and store their data in several arrays. The index of begin cell is stored
 * in NFA.
 *
 * @see DFA
 * @see Automata
 */
class NFA(
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
) : Automata() {
	companion object {
		fun from(vararg chars: Char) = NFABuilder.from(*chars).build()
		fun fromSorted(vararg chars: Char) = NFABuilder.fromSorted(*chars).build()
		fun fromSorted(chars: String) = NFABuilder.fromSorted(chars).build()
		fun from(charClass: CharClass) = NFABuilder.from(charClass).build()

		fun from(chars: CharSequence) = NFABuilder.from(chars).build()
		fun from(chars: Iterator<Char>) = NFABuilder.from(chars).build()
	}

	fun link(from: Int, to: Int) {
		outs[from].clear()
		outs[from].add(to)
	}

	override val size: Int
		get() = charClasses.size
	override var beginCell = finalCell
	val finalCell: Int
		get() = -1

	val indices: IntRange
		inline get() = 0 until size

	override fun copy(): NFA =
		NFA(
			charClasses.toMutableList(),
			outs.mapTo(mutableListOf()) { it.copy() }
		).also { it.beginCell = beginCell }

	private fun putInto(cellIndex: Int, list: CellList): Boolean {
		if (isFinal(cellIndex) || !isDummy(cellIndex)) {
			list.add(cellIndex)
			return isFinal(cellIndex)
		}
		var ret = isFinal(cellIndex)
		for (i in outs[cellIndex]) if (putInto(i, list)) ret = true
		return ret
	}

	private fun transit(cellIndex: Int, char: Char, list: CellList): Boolean {
		if (isFinal(cellIndex)) return false
		// When the cell is dummy, its CharClass should be empty and this following check will be satisfied.
		if (char !in charClasses[cellIndex]) return false
		var ret = isFinal(cellIndex)
		for (i in outs[cellIndex]) if (putInto(i, list)) ret = true
		return ret
	}

	fun match(chars: CharSequence, exact: Boolean = true) = match(chars.iterator(), exact)
	fun match(chars: Iterator<Char>, exact: Boolean = true) =
		CellList(this).apply { putInto(beginCell, this) }.match(chars, exact)

	operator fun plusAssign(other: NFA) {
		val offset = size
		charClasses += other.charClasses
		for (i in 0 until other.size)
			outs += other.outs[i].mapTo(emptyIntList()) { if (isFinal(it)) it else (it + offset) }
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

	fun appendCell(charClass: CharClass, outs: IntList = emptyIntList()): Int {
		this.charClasses += charClass
		this.outs += outs
		return size - 1
	}

	fun appendDummyCell(outs: IntList = emptyIntList()): Int = appendCell(CharClass.empty, outs)

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
		private val nfa: NFA,
		private val bitset: BitSet = BitSet(nfa.size),
		private val list: IntList = emptyIntList(),
		private var finalCount: Int = 0
	) {
		val size: Int
			get() = list.size

		val hasFinal: Boolean
			get() = finalCount != 0

		fun add(element: Int) {
			if (nfa.isFinal(element)) {
				++finalCount
				list.add(element)
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
				list.remove(element)
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
			var localHasFinal: Boolean
			do {
				val char = chars.next()
				localHasFinal = false
				for (cell in listA) {
					if ((!localHasFinal) && nfa.transit(cell, char, listB)) {
						localHasFinal = true
						if (!exact) return true
					}
				}
				val tmp = listA
				listA = listB
				listB = tmp
				listB.clear()
			} while (chars.hasNext())
			return localHasFinal
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

		internal fun transitionSet(marks: IntArray?): TransitionSet {
			val set = TransitionSet()
			for (cell in this) {
				if (nfa.isFinal(cell)) continue
				val ranges = nfa.charClasses[cell].ranges
				val list = CellList(nfa)
				for (out in nfa.outs[cell]) nfa.putInto(out, list)
				for (range in ranges)
					set.add(range, MutablePair(list, if (marks == null) 0 else marks[cell]))
			}
			set.optimize()
			return set
		}

		internal class TransitionSet : org.kiot.automata.TransitionSet<MutablePair<CellList, Int>>() {
			override fun copy(element: MutablePair<CellList, Int>) = MutablePair(element.first.copy(), element.second)

			override fun MutablePair<CellList, Int>.append(other: MutablePair<CellList, Int>) {
				if (second != 0 && other.second != 0) error("marks conflict")
				first.addAll(other.first.list)
				if (second == 0) second = other.second
			}
		}
	}

	fun toDFA(): DFA = toDFA(null).first

	/**
	 * Convert a NFA into DFA using Subset Construction.
	 */
	fun toDFA(marks: IntArray?): Pair<DFA, List<IntList>?> {
		require(marks == null || marks.size == size)
		val charRanges = mutableListOf<MutableList<PlainCharRange>>()
		val outs = mutableListOf<MutableList<Int>>()
		val finalFlags = emptyBooleanList()
		val queue = CircularIntQueue(size)

		// TODO maybe use mutableMapOf(LinkedHashMap) here?
		val cellMap = hashMapOf<CellList, Int>()
		val sets = mutableListOf<CellList.TransitionSet>()
		val transitionMarks = mutableListOf<IntList>()
		fun indexOf(list: CellList): Int =
			cellMap[list] ?: run {
				val index = charRanges.size
				cellMap[list] = index
				queue.push(index)
				sets.add(list.transitionSet(marks))
				charRanges.add(mutableListOf())
				outs.add(mutableListOf())
				transitionMarks.add(emptyIntList())
				finalFlags += list.hasFinal
				index
			}

		CellList(this).apply {
			putInto(beginCell, this)
			indexOf(this)
		}
		while (queue.isNotEmpty()) {
			val x = queue.pop()
			val set = sets[x]
			val myCharRanges = charRanges[x]
			val myOuts = outs[x]
			val newMarks = transitionMarks[x]
			for (pair in set) {
				myCharRanges.add(pair.first)
				myOuts.add(indexOf(pair.second.first))
				newMarks.add(pair.second.second)
			}
		}
		return Pair(
			DFA(charRanges, outs, finalFlags.toBitSet()),
			transitionMarks
		)
	}
}