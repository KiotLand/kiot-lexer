package org.kiot.automata

import org.kiot.util.BitSet
import org.kiot.util.CircularIntQueue
import org.kiot.util.IntList
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
			list += cellIndex
			return isFinal(cellIndex)
		}
		var ret = isFinal(cellIndex)
		for (i in outs[cellIndex]) ret = ret || putInto(i, list)
		return ret
	}

	private fun transit(cellIndex: Int, char: Char, list: CellList): Boolean {
		if (isFinal(cellIndex)) return false
		// When the cell is dummy, its CharClass should be empty and this following check will be satisfied.
		if (char !in charClasses[cellIndex]) return false
		var ret = isFinal(cellIndex)
		for (i in outs[cellIndex]) ret = ret || putInto(i, list)
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
	) : MutableSet<Int> {
		override val size: Int
			get() = list.size

		val hasFinal: Boolean
			get() = finalCount != 0

		override fun add(element: Int): Boolean {
			if (nfa.isFinal(element)) {
				++finalCount
				list.add(element)
				return true
			}
			if (bitset[element]) return false
			bitset.set(element)
			list.add(element)
			return true
		}

		override fun addAll(elements: Collection<Int>): Boolean {
			var ret = false
			for (element in elements) ret = ret || add(element)
			return ret
		}

		override fun remove(element: Int): Boolean {
			if (nfa.isFinal(element)) {
				--finalCount
				list.remove(element)
				return true
			}
			if (!bitset[element]) return false
			bitset.clear(element)
			list.remove(element)
			return true
		}

		override fun removeAll(elements: Collection<Int>): Boolean {
			var ret = false
			for (element in elements) ret = ret || remove(element)
			return ret
		}

		override fun retainAll(elements: Collection<Int>): Boolean {
			val ret = list.retainAll(elements)
			if (ret) {
				bitset.clear()
				for (element in list) {
					if (nfa.isFinal(element)) ++finalCount
					else bitset.set(element)
				}
			}
			return ret
		}

		override fun contains(element: Int): Boolean = bitset[element]

		override fun containsAll(elements: Collection<Int>): Boolean = elements.all { contains(it) }

		override fun isEmpty(): Boolean = size == 0

		override fun iterator(): MutableIterator<Int> = Iterator()

		override fun clear() {
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

		override fun hashCode(): Int = bitset.hashCode() * 31 + finalCount
		override fun equals(other: Any?): Boolean =
			if (other is CellList) finalCount == other.finalCount && bitset == other.bitset
			else false

		inner class Iterator : MutableIterator<Int> {
			private var index = 0

			override fun hasNext(): Boolean = index != list.size

			override fun next(): Int = list[index++]

			override fun remove() {
				if (index == 0) throw IllegalStateException()
				remove(list[--index])
			}
		}

		fun transitionSet(): TransitionSet {
			val set = TransitionSet()
			for (cell in this) {
				if (nfa.isFinal(cell)) continue
				val ranges = nfa.charClasses[cell].ranges
				val list = CellList(nfa)
				for (out in nfa.outs[cell]) nfa.putInto(out, list)
				for (range in ranges)
					set.add(range, list)
			}
			set.optimize()
			return set
		}

		class TransitionSet : org.kiot.automata.TransitionSet<CellList>() {
			override fun copy(element: CellList): CellList = element.copy()

			override fun CellList.append(other: CellList) {
				addAll(other)
			}
		}
	}

	/**
	 * Convert a NFA into DFA using Subset Construction.
	 */
	fun toDFA(): DFA {
		val charRanges = mutableListOf<MutableList<PlainCharRange>>()
		val outs = mutableListOf<MutableList<Int>>()
		val finalFlags = emptyBooleanList()
		val queue = CircularIntQueue(size)

		// TODO maybe use mutableMapOf(LinkedHashMap) here?
		val cellMap = hashMapOf<CellList, Int>()
		val sets = mutableListOf<CellList.TransitionSet>()
		fun indexOf(list: CellList): Int =
			cellMap[list] ?: run {
				val index = charRanges.size
				cellMap[list] = index
				queue.push(index)
				sets.add(list.transitionSet())
				charRanges.add(mutableListOf())
				outs.add(mutableListOf())
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
			for (pair in set) {
				myCharRanges.add(pair.first)
				myOuts.add(indexOf(pair.second))
			}
		}
		return DFA(charRanges, outs, finalFlags.toBitSet())
	}
}