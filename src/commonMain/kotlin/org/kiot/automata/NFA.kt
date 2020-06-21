package org.kiot.automata

import org.kiot.util.BitSet
import org.kiot.util.BooleanList
import org.kiot.util.IntList
import org.kiot.util.intListOf

/**
 * NFA stands for "Nondeterministic finite automata".
 *
 * In kiot-lexer, it's implemented through representing states as integers
 * and store their data in several arrays. We store the begin cell at index 0.
 *
 * For convenience, we store a end cell which does not exist in general NFA. It
 * should have only one out. Note that the end cell does not have to be a final cell.
 * For example, we have a NFA that matches "AAB", like:
 *
 * A -> A -> B -> (Final)
 *
 * In the NFA above, cell "B" is the end cell instead of (Final).
 *
 * @see [Automata]
 */
class NFA private constructor(
		/**
		 * In kiot-lexer, transitions are stored in cells themselves. Each
		 * cell has a [CharClass]. Let A be a cell, and its [CharClass]
		 * be C. If we are now at cell A, and we received a new char
		 * that is contained in C, so we'll extend the [CellList] with
		 * A's [outs], otherwise we'll do nothing.
		 *
		 * Specially, if a cell's [CharClass] is empty, we'll treat it as
		 * a dummy cell which does not accept any char and only transits
		 * its epsilon when being stepped in.
		 */
		private val charClasses: MutableList<CharClass>,
		private val outs: IntList, // Mentioned above
		/**
		 * When stepped in a cell, all the cells connected to it with epsilon edges will
		 * also be added to [CellList] without condition. In kiot-lexer, we store all
		 * the cells that is connected to a cell with epsilon edges in this list.
		 */
		private val epsilons: MutableList<IntList>,
		/**
		 * Whether a cell is a final cell.
		 */
		private val finalFlags: BooleanList
) : Automata() {
	companion object {
		fun chain(vararg elements: NFA): NFA {
			if (elements.isEmpty()) throw IllegalArgumentException()
			if (elements.size == 1) return elements[0].copy()
			val ret = NFA()
			for (i in 0 until elements.lastIndex) {
				ret += elements[i]
				ret.link(ret.endCell, ret.size)
			}
			ret += elements.last()
			ret.reduce()
			return ret
		}

		fun branch(vararg branches: NFA): NFA {
			if (branches.isEmpty()) throw IllegalArgumentException()
			if (branches.size == 1) return branches[0].copy()
			val ret = NFA()
			/*
                        /--> (NFA1) --\
			(Start) --<      ......     >--> (End) --> (Final)
			            \--> (NFAn) --/
			 */
			ret.appendCell(CharClass.empty)
			val beginEpsilons = ret.epsilons[0]
			val endCell = ret.appendDummyCell(intListOf(2))
			ret.appendFinalCell()
			for (branch in branches) {
				beginEpsilons += ret.size
				ret += branch
				ret.link(ret.endCell, endCell)
			}
			ret.endCell = endCell
			ret.reduce()
			return ret
		}

		fun from(vararg chars: Char): NFA = from(CharClass.from(*chars))
		fun fromSorted(vararg chars: Char): NFA = from(CharClass.fromSorted(*chars))
		fun fromSorted(chars: String): NFA = from(CharClass.fromSorted(chars))
		fun from(charClass: CharClass): NFA = NFA().apply {
			appendCell(charClass, 1, IntList(), false)
			appendFinalCell()
		}

		fun from(chars: CharSequence) = from(chars.iterator())
		fun from(iterator: Iterator<Char>): NFA {
			if (!iterator.hasNext()) throw IllegalArgumentException()
			val ret = NFA()
			ret.appendCell(CharClass.from(iterator.next()))
			var cur = 0
			while (iterator.hasNext()) {
				ret.appendCell(CharClass.from(iterator.next()))
				ret.setOut(cur, ++cur)
			}
			ret.setOut(cur, ret.appendFinalCell())
			ret.endCell = cur
			return ret
		}
	}

	fun oneOrMore(): NFA {
		/*
           |---------------------|
           √                     |
		(Start) --> (End) --> (Dummy1) --> (Dummy2) --> (Final)
		 */
		val ret = NFA(this)
		val dummy2 = ret.appendDummyCell(intListOf(ret.outs[ret.endCell]))
		ret.outs[ret.endCell] = ret.appendDummyCell(intListOf(ret.beginCell, dummy2)) // dummy1
		ret.endCell = dummy2
		return ret
	}

	fun link(from: Int, to: Int) {
		if (isDummy(from)) epsilons[from].add(to)
		else outs[from] = to
	}

	constructor(vararg all: NFA) : this(mutableListOf(), IntList(), mutableListOf(), BooleanList()) {
		for (one in all) this += one
	}

	override val size: Int
		get() = charClasses.size
	val beginCell: Int
		get() = 0
	var endCell = 0

	override fun copy(): NFA =
			NFA(
					charClasses.toMutableList(),
					outs.copy(),
					epsilons.mapTo(mutableListOf()) { it.copy() },
					finalFlags.copy()
			).also { it.endCell = endCell }

	private fun putInto(cellIndex: Int, list: CellList): Boolean {
		if (!isDummy(cellIndex)) list += cellIndex
		var ret = finalFlags[cellIndex]
		for (i in epsilons[cellIndex]) ret = ret || putInto(i, list)
		return ret
	}

	private fun transit(cellIndex: Int, char: Char, list: CellList): Boolean {
		if (char !in charClasses[cellIndex]) return false
		return putInto(outs[cellIndex], list)
	}

	fun match(chars: CharSequence, exact: Boolean = true) = match(chars.iterator(), exact)
	fun match(chars: Iterator<Char>, exact: Boolean = true) = CellList().apply { putInto(beginCell, this) }.match(chars, exact)

	operator fun plusAssign(other: NFA) {
		val offset = size
		charClasses += other.charClasses
		for (i in 0 until other.size) {
			outs += other.outs[i] + offset
			epsilons += other.epsilons[i].mapTo(IntList()) { it + offset }
		}
		finalFlags += other.finalFlags
		endCell = offset + other.endCell
	}

	fun isDummy(cellIndex: Int) = charClasses[cellIndex].isEmpty()
	fun charClassOf(cellIndex: Int) = charClasses[cellIndex]
	fun outOf(cellIndex: Int) = outs[cellIndex]
	fun epsilonOf(cellIndex: Int) = epsilons[cellIndex]
	fun isFinal(cellIndex: Int) = finalFlags[cellIndex]

	fun setCharClass(cellIndex: Int, charClass: CharClass) {
		this.charClasses[cellIndex] = charClass
	}

	fun setOut(cellIndex: Int, out: Int) {
		this.outs[cellIndex] = out
	}

	fun setEpsilon(cellIndex: Int, epsilon: IntList) {
		this.epsilons[cellIndex] = epsilon
	}

	fun setFinal(cellIndex: Int, final: Boolean) {
		this.finalFlags[cellIndex] = final
	}

	fun appendDummyCell(epsilon: IntList, final: Boolean = false): Int = appendCell(CharClass.empty, 0, epsilon, final)

	fun appendCell(charClass: CharClass, out: Int = 0, epsilon: IntList = IntList(), final: Boolean = false): Int {
		this.charClasses += charClass
		this.outs += out
		this.epsilons += epsilon
		this.finalFlags += final
		return size - 1
	}

	fun appendFinalCell() = appendDummyCell(IntList(), true)

	fun clear() {
		charClasses.clear()
		outs.clear()
		epsilons.clear()
		finalFlags.clear()
	}

	/**
	 * Remove unused cells (cells that cannot be reached from the begin cell) and
	 * return the amount of them.
	 */
	fun reduce(): Int {
		val visited = BitSet(size)
		val stack = intListOf(0)
		visited.set(0)
		while (stack.isNotEmpty()) {
			val x = stack.removeAt(stack.lastIndex)
			if (!visited[outs[x]]) {
				visited.set(outs[x])
				if (!isDummy(x)) stack += outs[x]
			}
			for (y in epsilons[x]) {
				if (visited[y]) continue
				visited.set(y)
				stack += y
			}
		}
		val map = IntArray(size)
		var pre = 0
		fun removeRange(fromIndex: Int, toIndex: Int) {
			val from = fromIndex - pre
			val to = toIndex - pre
			charClasses.subList(from, to).clear()
			outs.removeRange(from, to)
			epsilons.subList(from, to).clear()
			finalFlags.removeRange(from, to)
			map[fromIndex] -= toIndex - fromIndex
			pre += toIndex - fromIndex
		}

		var lst = -1
		var ret = 0
		val originalSize = size
		for (i in 0 until size) {
			if (!visited[i]) {
				if (lst == -1) lst = i
			} else if (lst != -1) {
				ret += i - lst
				removeRange(lst, i)
				lst = -1
			}
		}
		if (lst != -1) {
			ret += originalSize - lst
			removeRange(lst, originalSize)
		}
		for (j in 1 until map.size) map[j] += map[j - 1]
		for (j in map.indices) map[j] += j
		for (j in 0 until size) {
			outs[j] = map[outs[j]]
			for (k in epsilons[j].indices) epsilons[j][k] = map[epsilons[j][k]]
		}
		return ret
	}

	/**
	 * A list of NFA cells.
	 */
	private inner class CellList(
			val bitset: BitSet = BitSet(size),
			val list: IntList = IntList(),
			/**
			 * How many final cells are there in this instance?
			 */
			var finalCount: Int = 0
	) : MutableSet<Int> {
		override val size: Int
			get() = list.size

		override fun add(element: Int): Boolean {
			if (bitset[element]) return false
			bitset.set(element)
			list.add(element)
			if (isFinal(element)) ++finalCount
			return true
		}

		override fun addAll(elements: Collection<Int>): Boolean {
			var ret = false
			for (element in elements) ret = ret || add(element)
			return ret
		}

		override fun remove(element: Int): Boolean {
			if (!bitset[element]) return false
			bitset.clear(element)
			list.remove(element)
			if (isFinal(element)) --finalCount
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
				finalCount = 0
				for (element in list) {
					bitset.set(element)
					if (isFinal(element)) ++finalCount
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

		fun copy(): CellList = CellList(bitset.copy(), list.copy(), finalCount)

		fun match(chars: CharSequence, exact: Boolean = true) = match(chars.iterator(), exact)
		fun match(chars: kotlin.collections.Iterator<Char>, exact: Boolean = true): Boolean {
			if (!chars.hasNext()) return finalCount != 0
			if ((!exact) && finalCount != 0) return true
			var listA = copy()
			var listB = CellList()
			var hasFinal: Boolean
			do {
				val char = chars.next()
				hasFinal = false
				for (cell in listA) {
					if ((!hasFinal) && transit(cell, char, listB)) {
						hasFinal = true
						if (!exact) return true
					}
				}
				val tmp = listA
				listA = listB
				listB = tmp
				listB.clear()
			} while (chars.hasNext())
			return hasFinal
		}

		inner class Iterator : MutableIterator<Int> {
			var index = 0

			override fun hasNext(): Boolean = index != list.size

			override fun next(): Int = list[index++]

			override fun remove() {
				if (index == 0) throw IllegalStateException()
				remove(list[--index])
			}
		}
	}
}