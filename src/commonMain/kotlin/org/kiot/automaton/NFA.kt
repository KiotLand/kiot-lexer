package org.kiot.automaton

import org.kiot.util.BitSet
import org.kiot.util.intListOf

/**
 * With an additional property: [endCell], we can now build a
 * NFA in a sequential way!
 *
 * End cell should have only one out to the final cell (-1),
 * for example:
 *
 * A -> A -> B -> (Final)
 *
 * In the NFA above, cell "B" is the end cell.
 *
 * @author Mivik
 */
class NFA(val nfa: StaticNFA = StaticNFA(), var endCell: Int = 0) {
	companion object {
		fun chain(vararg elements: NFA) =
			NFA().apply { for (element in elements) append(element) }

		fun branch(vararg branches: NFA) = NFA().appendBranch(branches.asList())
		fun branch(branches: List<NFA>) = NFA().appendBranch(branches)

		fun from(vararg chars: Char) = from(CharClass.from(*chars))
		fun fromSorted(vararg chars: Char) = from(CharClass.fromSorted(*chars))
		fun fromSorted(chars: String) = from(CharClass.fromSorted(chars))
		fun from(charClass: CharClass) = NFA().append(charClass)

		fun from(chars: CharSequence) = from(chars.iterator())
		fun from(chars: Iterator<Char>): NFA {
			require(chars.hasNext()) { "Chars can not be empty" }
			return NFA().append(chars)
		}

		fun fromRegExp(regexp: String) = regexp.regexp()
	}

	var beginCell: Int
		inline get() = nfa.beginCell
		inline set(value) {
			nfa.beginCell = value
		}
	val size: Int
		inline get() = nfa.size

	fun extend(cellIndex: Int) {
		if (nfa.isFinal(beginCell)) nfa.beginCell = cellIndex
		else nfa.link(endCell, cellIndex)
	}

	fun makeEnd(cellIndex: Int) {
		nfa.link(cellIndex, nfa.finalCell)
		endCell = cellIndex
	}

	fun extendEnd(cellIndex: Int) {
		extend(cellIndex)
		makeEnd(cellIndex)
	}

	fun copy(): NFA = NFA(nfa.copy(), endCell)

	fun clear() {
		nfa.clear()
		endCell = 0
	}

	fun isEmpty() = endCell == -1
	fun isNotEmpty() = endCell != -1

	fun append(other: NFA): NFA {
		if (other.isEmpty()) return this
		extend(other.beginCell + size)
		include(other)
		return this
	}

	fun appendBranch(vararg branches: NFA) = appendBranch(branches.asList())
	fun appendBranch(branches: List<NFA>): NFA {
		if (branches.isEmpty()) return this
		if (branches.size == 1) return append(branches[0])
		/*
		            /--> (NFA1) --\
		(Begin) --<      ......     >--> (End) --> (Final)
		            \--> (NFAn) --/
		 */
		val newBegin = nfa.appendDummyCell()
		extend(newBegin)
		val beginOuts = nfa.outsOf(newBegin)
		val newEnd = nfa.appendDummyCell()
		for (branch in branches) {
			beginOuts += branch.beginCell + size
			include(branch)
			nfa.link(endCell, newEnd)
		}
		makeEnd(newEnd)
		return this
	}

	fun append(chars: CharSequence) = append(chars.iterator())
	fun append(chars: Iterator<Char>): NFA {
		if (!chars.hasNext()) return this
		var cur = nfa.appendCell(CharClass.from(chars.next()))
		extend(cur)
		while (chars.hasNext()) {
			nfa.appendCell(CharClass.from(chars.next()))
			nfa.outsOf(cur).add(++cur)
		}
		makeEnd(cur)
		return this
	}

	/**
	 * Four functions below add one simple cell to NFA that
	 * accepts single char.
	 */
	fun append(vararg chars: Char) = append(CharClass.from(*chars))
	fun appendSorted(vararg chars: Char) = append(CharClass.from(*chars))
	fun appendSorted(chars: String) = append(CharClass.fromSorted(chars))
	fun append(charClass: CharClass): NFA {
		extendEnd(nfa.appendCell(charClass))
		return this
	}

	fun appendRegExp(regexp: String) = append(regexp.regexp())

	/**
	 * Remove unused cells (cells that cannot be reached from the begin cell) and
	 * return the amount of them.
	 */
	fun reduce(): Int = with(nfa) {
		val visited = BitSet(size)
		val stack = intListOf(beginCell)
		visited.set(beginCell)
		while (stack.isNotEmpty()) {
			val x = stack.removeAt(stack.lastIndex)
			for (y in outsOf(x)) {
				if (isFinal(y) || visited[y]) continue
				visited.set(y)
				stack += y
			}
		}
		val map = IntArray(size)
		var pre = 0
		fun removeRange(fromIndex: Int, toIndex: Int) {
			val from = fromIndex - pre
			val to = toIndex - pre
			clearRange(from, to)
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
		for (j in indices) {
			val outs = outsOf(j)
			for (k in outs.indices)
				if (!isFinal(outs[k])) outs[k] = map[outs[k]]
		}
		// Important!
		nfa.beginCell = map[nfa.beginCell]
		endCell = map[endCell]
		return ret
	}

	fun match(chars: CharSequence, exact: Boolean = true) = match(chars.iterator(), exact)
	fun match(chars: Iterator<Char>, exact: Boolean = true) = nfa.match(chars, exact)

	fun static() = nfa

	fun include(other: NFA) {
		val offset = nfa.size
		nfa += other.nfa
		endCell = offset + other.endCell
	}

	/**
	 * Make this NFA a new NFA that accepts (this)+ .
	 */
	fun oneOrMore(): NFA {
		/*
		   |---------------------|
		   √                     |
		(Begin) --> (End) --> (Dummy1) --> (Dummy2) --> (Final)
		 */
		with(nfa) {
			val dummy2 = appendDummyCell()
			extend(appendDummyCell(intListOf(beginCell, dummy2))) // dummy1
			makeEnd(dummy2)
		}
		return this
	}

	/**
	 * Make this NFA a new NFA that accepts (this)? .
	 */
	fun unnecessary(): NFA {
		/*
		   |-----------------------------------|
		   |                                   √
		(Dummy1) --> (Begin) --> (End) --> (Dummy2) --> (Final)
		 */
		with(nfa) {
			val dummy2 = appendDummyCell()
			extend(dummy2)
			makeEnd(dummy2)
			beginCell = appendDummyCell(intListOf(beginCell, dummy2)) // dummy1
		}
		return this
	}

	/**
	 * Make this NFA a new NFA that accepts (this)* .
	 */
	fun any(): NFA {
		/*
		   |-----------------------------------|
		   |                                   √
		(Dummy1) --> (Begin) --> (End)      (Dummy2) --> (Final)
		   ^                       |
		   |-----------------------|
		 */
		with(nfa) {
			val dummy2 = appendDummyCell()
			val dummy1 = appendDummyCell(intListOf(beginCell, dummy2))
			extend(dummy1)
			beginCell = dummy1
			makeEnd(dummy2)
		}
		return this
	}

	fun repeat(start: Int, endInclusive: Int): NFA {
		require(start in 0..endInclusive) { "Illegal repeating range" }
		if (start == 0 && endInclusive == 1) return unnecessary()
		/*
		                                  |-------------------------------------------------------|
		                                  |                                                       √
		((Begin) --> (End))*start --> ((Dummy) --> (Begin) --> (End))*(endInclusive-start) --> (Final)
		 */
		val backup = copy()
		clear()
		repeat(start) {
			append(backup)
		}
		backup.beginCell = backup.nfa.appendDummyCell(intListOf(backup.beginCell, backup.nfa.finalCell))
		repeat(endInclusive - start) {
			append(backup)
		}
		return this
	}

	fun repeatAtLeast(time: Int): NFA {
		require(time >= 0) { "Illegal repeating range" }
		if (time == 0) return any()
		if (time == 1) return oneOrMore()
		/*
		((Begin) --> (End))*time --> ((Any))
		 */
		val backup = copy()
		clear()
		repeat(time) {
			append(backup)
		}
		append(backup.any())
		return this
	}
}