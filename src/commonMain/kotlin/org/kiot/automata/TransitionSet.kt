package org.kiot.automata

import org.kiot.util.contentEquals

abstract class TransitionSet<T : Any> : Iterable<Pair<PlainCharRange, T>> {
	val ranges = mutableListOf<PlainCharRange>()
	val targets = mutableListOf<T?>() // null means a placeholder
	private var optimized = true

	init {
		ranges += PlainCharRange.fullRange
		targets.add(null)
	}

	abstract fun copy(element: T): T
	abstract fun T.append(other: T)

	val size: Int
		get() = ranges.size

	private fun split(char: Char): Int {
		optimized = false
		var l = 0
		var r = size - 1
		var ret = 0
		while (l <= r) {
			val mid = (l + r) ushr 1
			if (ranges[mid].end >= char) {
				ret = mid
				r = mid - 1
			} else l = mid + 1
		}
		val origin = ranges[ret]
		if (char == origin.start) return ret
		ranges[ret] = PlainCharRange(char, origin.end)
		ranges.add(ret, PlainCharRange(origin.start, char - 1))
		targets.add(ret, targets[ret]?.let { copy(it) })
		return ret + 1
	}

	fun add(range: PlainCharRange, target: T) {
		val lef = split(range.start)
		val rig = if (range.end == Char.MAX_VALUE) size else split(range.end + 1)
		for (i in lef until rig) {
			targets[i] = targets[i].run {
				if (this == null) copy(target)
				else {
					append(target)
					this
				}
			}
		}
	}

	fun optimize() {
		if (optimized) return
		var i = 0
		while (i < size - 1) {
			val j = i + 1
			val origin = targets[i]
			var last = ranges[i].end
			while (j != size && targets[j] == origin) {
				last = ranges[j].end
				ranges.removeAt(j)
				targets.removeAt(j)
			}
			ranges[i] = PlainCharRange(ranges[i].start, last)
			++i
		}
		optimized = true
	}

	override fun iterator(): Iterator<Pair<PlainCharRange, T>> {
		return object : Iterator<Pair<PlainCharRange, T>> {
			var ind = 0

			init {
				find()
			}

			fun find() {
				while (ind != size && targets[ind] == null) ++ind
			}

			override fun hasNext(): Boolean = ind != size

			override fun next(): Pair<PlainCharRange, T> =
				Pair(ranges[ind], targets[ind]!!).also {
					++ind
					find()
				}
		}
	}


	override fun equals(other: Any?): Boolean =
		if (other is TransitionSet<*>) contentEquals(other)
		else false

	override fun hashCode(): Int {
		var result = ranges.hashCode()
		result = 31 * result + targets.hashCode()
		return result
	}
}