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
 * @see Automata
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
		 * to all its outs when being stepped in.
		 */
		private val charClasses: MutableList<CharClass>,
		private val outs: MutableList<IntList>, // Mentioned above
		/**
		 * Whether a cell is a final cell.
		 */
		private val finalFlags: BooleanList
) : Automata() {
	companion object {
		fun chain(vararg elements: NFA): NFA {
			if (elements.isEmpty()) throw IllegalArgumentException()
			if (elements.size == 1) return elements[0].copy()
			return NFA().apply {
				for (i in 0 until elements.lastIndex) {
					this += elements[i]
					link(endCell, elements[i + 1].beginCell + size)
				}
				this += elements.last()
				reduce()
			}
		}

		fun branch(vararg branches: NFA): NFA {
			if (branches.isEmpty()) throw IllegalArgumentException()
			if (branches.size == 1) return branches[0].copy()
			/*
                        /--> (NFA1) --\
			(Begin) --<      ......     >--> (End) --> (Final)
			            \--> (NFAn) --/
			 */
			return NFA().apply {
				appendCell(CharClass.empty)
				val beginOuts = outs[0]
				val endCell = appendDummyCell(intListOf(2))
				appendFinalCell()
				for (branch in branches) {
					beginOuts += branch.beginCell + size
					this += branch
					link(endCell, endCell)
				}
				this.endCell = endCell
				reduce()
			}
		}

		fun from(vararg chars: Char): NFA = from(CharClass.from(*chars))
		fun fromSorted(vararg chars: Char): NFA = from(CharClass.fromSorted(*chars))
		fun fromSorted(chars: String): NFA = from(CharClass.fromSorted(chars))
		fun from(charClass: CharClass): NFA = NFA().apply {
			appendCell(charClass, intListOf(1), false)
			appendFinalCell()
		}

		fun from(chars: CharSequence) = from(chars.iterator())
		fun from(iterator: Iterator<Char>): NFA {
			if (!iterator.hasNext()) throw IllegalArgumentException()
			return NFA().apply {
				appendCell(CharClass.from(iterator.next()))
				var cur = 0
				while (iterator.hasNext()) {
					appendCell(CharClass.from(iterator.next()))
					outs[cur].add(cur + 1)
					++cur
				}
				outs[cur].add(appendFinalCell())
				endCell = cur
			}
		}
	}

	/**
	 * Create a NFA that accepts (this)+ .
	 */
	fun oneOrMore(): NFA {
		/*
           |---------------------|
           √                     |
		(Begin) --> (End) --> (Dummy1) --> (Dummy2) --> (Final)
		 */
		return NFA(this).apply {
			val dummy2 = appendDummyCell(outs[endCell])
			outs[endCell] = IntList()
			link(endCell, appendDummyCell(intListOf(beginCell, dummy2))) // dummy1
			endCell = dummy2
		}
	}

	/**
	 * Create a NFA that accepts (this)? .
	 */
	fun unnecessary(): NFA {
		/*
		   |-----------------------------------|
		   |                                   √
		(Dummy1) --> (Begin) --> (End) --> (Dummy2) --> (Final)
		 */
		return NFA(this).apply {
			val dummy2 = appendDummyCell(outs[endCell])
			outs[endCell] = IntList()
			link(endCell, dummy2)
			beginCell = appendDummyCell(intListOf(beginCell, dummy2)) // dummy1
			endCell = dummy2
		}
	}

	/**
	 * Create a automata that accepts (this)* .
	 */
	fun any(): NFA {
		/*
		   |-----------------------------------|
		   |                                   √
		(Dummy1) --> (Begin) --> (End)      (Dummy2) --> (Final)
		   ^                       |
		   |-----------------------|
		 */
		return NFA(this).apply {
			val dummy2 = appendDummyCell(outs[endCell])
			outs[endCell] = IntList()
			val dummy1 = appendDummyCell(intListOf(beginCell, dummy2))
			link(endCell, dummy1)
			beginCell = dummy1
			endCell = dummy2
		}
	}

	fun link(from: Int, to: Int) {
		outs[from].clear()
		outs[from].add(to)
	}

	constructor(vararg all: NFA) : this(mutableListOf(), mutableListOf(), BooleanList()) {
		for (one in all) this += one
	}

	override val size: Int
		get() = charClasses.size
	var beginCell = 0
	var endCell = 0

	val indices: IntRange
		inline get() = 0 until size

	override fun copy(): NFA =
			NFA(
					charClasses.toMutableList(),
					outs.mapTo(mutableListOf()) { it.copy() },
					finalFlags.copy()
			).also {
				it.beginCell = beginCell
				it.endCell = endCell
			}

	private fun putInto(cellIndex: Int, list: CellList): Boolean {
		if (isFinal(cellIndex) || !isDummy(cellIndex)) {
			list += cellIndex
			return finalFlags[cellIndex]
		}
		var ret = finalFlags[cellIndex]
		for (i in outs[cellIndex]) ret = ret || putInto(i, list)
		return ret
	}

	private fun transit(cellIndex: Int, char: Char, list: CellList): Boolean {
		// When the cell is dummy, its CharClass should be empty and this following check will be satisfied.
		if (char !in charClasses[cellIndex]) return false
		var ret = finalFlags[cellIndex]
		for (i in outs[cellIndex]) ret = ret || putInto(i, list)
		return ret
	}

	fun match(chars: CharSequence, exact: Boolean = true) = match(chars.iterator(), exact)
	fun match(chars: Iterator<Char>, exact: Boolean = true) = CellList().apply { putInto(beginCell, this) }.match(chars, exact)

	operator fun plusAssign(other: NFA) {
		val offset = size
		charClasses += other.charClasses
		for (i in 0 until other.size)
			outs += other.outs[i].mapTo(IntList()) { it + offset }
		finalFlags += other.finalFlags
		// note that we update end cell only.
		endCell = offset + other.endCell
	}

	fun isDummy(cellIndex: Int) = charClasses[cellIndex].isEmpty()
	fun charClassOf(cellIndex: Int) = charClasses[cellIndex]
	fun outOf(cellIndex: Int) = outs[cellIndex]
	fun isFinal(cellIndex: Int) = finalFlags[cellIndex]

	fun setCharClass(cellIndex: Int, charClass: CharClass) {
		this.charClasses[cellIndex] = charClass
	}

	fun setOuts(cellIndex: Int, outs: IntList) {
		this.outs[cellIndex] = outs
	}

	fun setFinal(cellIndex: Int, final: Boolean) {
		this.finalFlags[cellIndex] = final
	}

	fun appendCell(charClass: CharClass, outs: IntList = IntList(), final: Boolean = false): Int {
		this.charClasses += charClass
		this.outs += outs
		this.finalFlags += final
		return size - 1
	}

	fun appendDummyCell(outs: IntList, final: Boolean = false): Int = appendCell(CharClass.empty, outs, final)
	fun appendFinalCell() = appendDummyCell(IntList(), true)

	fun clear() {
		charClasses.clear()
		outs.clear()
		finalFlags.clear()
	}

	/**
	 * Remove unused cells (cells that cannot be reached from the begin cell) and
	 * return the amount of them.
	 */
	fun reduce(): Int {
		val visited = BitSet(size)
		val stack = intListOf(beginCell)
		visited.set(beginCell)
		while (stack.isNotEmpty()) {
			val x = stack.removeAt(stack.lastIndex)
			for (y in outs[x]) {
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
			outs.subList(from, to).clear()
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
		for (j in indices)
			for (k in outs[j].indices) outs[j][k] = map[outs[j][k]]
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