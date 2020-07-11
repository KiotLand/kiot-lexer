package org.kiot.automata

import org.kiot.util.Binarizable
import org.kiot.util.Binarizer
import org.kiot.util.Binary
import org.kiot.util.StaticBinarizer
import org.kiot.util.binarySize

/**
 * Alternative to [CharRange] in kotlin-stdlib.
 *
 * Since [CharRange] in kotlin-stdlib is actually a subclass of
 * [CharProgression], it requires additional "step" field, which
 * is actually useless in this project.
 *
 * [CharRange] can be converted to [PlainCharRange] using [CharRange].[plain],
 * and using [plainTo] instead of ".." can construct a [PlainCharRange] with two
 * operands.
 *
 * @see CharClass
 * @see plainTo
 *
 * @author Mivik
 */
data class PlainCharRange(val start: Char, val end: Char) : Binarizable {
	companion object {
		val fullRange = PlainCharRange(Char.MIN_VALUE, Char.MAX_VALUE)
		val empty = PlainCharRange(1.toChar(), 0.toChar())

		val binarizer = object : StaticBinarizer<PlainCharRange> {
			override fun binarize(bin: Binary, value: PlainCharRange) = value.run {
				bin.put(start)
				bin.put(end)
			}

			override fun debinarize(bin: Binary) = PlainCharRange(bin.char(), bin.char())

			override val binarySize: Int
				get() = Char.binarySize * 2
		}.also { Binary.register(it) }

		val binarySize: Int
			get() = binarizer.binarySize
	}

	operator fun compareTo(other: PlainCharRange) =
		start.compareTo(other.start).let {
			if (it == 0) end.compareTo(other.end) else it
		}

	operator fun contains(char: Char): Boolean = char in start..end

	override fun toString(): String = "[$start..$end]"

	fun copy() = PlainCharRange(start, end)

	/**
	 * Convert this [PlainCharRange] to String that wraps all the chars in this
	 * range (sorted) with square brackets. Used for pretty printing in cases like debugging.
	 */
	fun expand(): String =
		buildString(end - start + 1) {
			append('[')
			for (i in start..end) append(i)
			append(']')
		}
}

infix fun Char.plainTo(other: Char) = PlainCharRange(this, other)

fun CharRange.plain() = PlainCharRange(start, endInclusive)

/**
 * A combination of several [PlainCharRange], representing a general char set
 * while [PlainCharRange] can only represent a continuous char range.
 *
 * @see PlainCharRange
 *
 * @author Mivik
 */
data class CharClass(val ranges: List<PlainCharRange>) : Binarizable {
	companion object {
		// Several useful char class constants.
		val empty = CharClass()
		val any = CharClass(Char.MIN_VALUE plainTo Char.MAX_VALUE)
		val digit = CharClass('0' plainTo '9')
		val letter = CharClass('A' plainTo 'Z', 'a' plainTo 'z')
		val blank = CharClass(
			'\t' plainTo '\t',
			'\n' plainTo '\n',
			'\u000b' plainTo '\u000b',
			'\u000c' plainTo '\u000c',
			'\r' plainTo '\r',
			' ' plainTo ' '
		)

		/**
		 * Obtain [CharClass] from several specified chars.
		 *
		 * @see fromSorted
		 */
		fun from(vararg chars: Char) =
			fromSorted(*chars.also { it.sort() })

		/**
		 * Obtain [CharClass] from a sorted string, equivalent to `fromSortedChars({all chars in [chars]})`
		 */
		fun fromSorted(chars: String) =
			fromSorted(*CharArray(chars.length).apply { for (i in chars.indices) this[i] = chars[i] })

		/**
		 * Obtain [CharRange] from several chars, requiring the given
		 * char sequence is sorted.
		 *
		 * @see from
		 */
		fun fromSorted(vararg chars: Char): CharClass {
			if (chars.isEmpty()) return empty
			var start = chars[0]
			var end = chars[0]
			val list = mutableListOf<PlainCharRange>()
			for (i in 1 until chars.size) {
				val char = chars[i]
				if (char == end + 1) end = char
				else {
					list.add(start plainTo end)
					start = char
					end = char
				}
			}
			if (list.isEmpty() || list.last().start != start)
				list.add(start plainTo end)
			return CharClass(list)
		}

		/**
		 * Obtain [CharRange] from several [PlainCharRange], requiring the given
		 * ranges are sorted and DO NOT OVERLAP.
		 *
		 * @see from
		 */
		fun fromSorted(chars: List<PlainCharRange>): CharClass {
			if (chars.isEmpty()) return empty
			var start = chars[0].start
			var end = chars[0].end
			val list = mutableListOf<PlainCharRange>()
			for (i in 1 until chars.size) {
				val range = chars[i]
				if (range.start == end + 1) end = range.end
				else {
					list.add(start plainTo end)
					start = range.start
					end = range.end
				}
			}
			if (list.isEmpty() || list.last().start != start)
				list.add(start plainTo end)
			return CharClass(list)
		}

		val binarizer = object : Binarizer<CharClass> {
			override fun binarize(bin: Binary, value: CharClass) = bin.putList(value.ranges)
			override fun debinarize(bin: Binary) = CharClass(bin.readList())
			override fun measure(value: CharClass): Int = Binary.measureList(value.ranges)
		}.also { Binary.register(it) }
	}

	constructor(vararg ranges: PlainCharRange) : this(ranges.asList())

	fun isEmpty() = ranges.isEmpty()
	fun isNotEmpty() = ranges.isNotEmpty()

	/**
	 * Determine whether a char is consist in this [CharClass] with
	 * a time complexity of O(log(N)), in which N stands for the amount of
	 * [PlainCharRange] contained in the instance.
	 */
	operator fun contains(char: Char): Boolean {
		var lef = 0
		var rig = ranges.lastIndex
		// Binary search.
		while (lef <= rig) {
			val mid = (lef + rig) ushr 1
			if (char >= ranges[mid].start) {
				if (char <= ranges[mid].end) return true
				lef = mid + 1
			} else rig = mid - 1
		}
		return false
	}

	/**
	 * Find the index of [PlainCharRange] that contains [char].
	 * If there's no such [PlainCharRange], returns -1.
	 */
	fun indexOf(char: Char): Int {
		var lef = 0
		var rig = ranges.lastIndex
		var ret: Int = -1
		while (lef <= rig) {
			val mid = (lef + rig) ushr 1
			if (char >= ranges[mid].start) {
				lef = mid + 1
				ret = mid
			} else rig = mid - 1
		}
		return if (ret == -1 || char !in ranges[ret]) -1 else ret
	}

	/**
	 * Merge two [CharClass] into one with a time complexity of O(N),
	 * where N stands for the amount of [PlainCharRange] in a [CharClass].
	 */
	fun merge(other: CharClass): CharClass {
		if (isEmpty()) return other
		if (other.isEmpty()) return this
		var i = 0
		var j = 0
		val list = mutableListOf<PlainCharRange>()

		// Which side should be considered first?
		fun side(): Boolean =
			if (i == ranges.size) true
			else if (j == other.ranges.size) false
			else ranges[i] > other.ranges[j]
		while (i != ranges.size || j != other.ranges.size) {
			val start: Char
			var end: Char
			(if (side()) other.ranges[j++] else ranges[i++]).let {
				start = it.start
				end = it.end + 1
			}

			// Merge incoming PlainCharRange that overlap.
			while (i != ranges.size || j != other.ranges.size) {
				val side = side()
				val range = if (side) other.ranges[j] else ranges[i]
				if (range.start <= end) {
					end = range.end + 1
					if (side) ++j else ++i
				} else break
			}
			list += PlainCharRange(start, end - 1)
		}
		return CharClass(list)
	}

	override fun toString(): String = "{${ranges.joinToString(", ")}}"

	/**
	 * Obtain the inverse of the [CharClass], which means chars are contained
	 * in the returning [CharClass] if and only if they're not contained in
	 * this [CharClass].
	 */
	fun inverse(): CharClass {
		if (ranges.isEmpty()) return any
		var lst = Char.MIN_VALUE
		val list = ArrayList<PlainCharRange>(ranges.size + 1)
		for (i in ranges) {
			if (i.start != Char.MIN_VALUE && i.start != lst)
				list.add(lst plainTo i.start - 1)
			lst = i.end + 1
		}
		if (ranges.last().end != Char.MAX_VALUE) list.add(lst plainTo Char.MAX_VALUE)
		return CharClass(list)
	}

	/**
	 * Convert this [CharClass] to String that wraps all the chars in this char
	 * class (sorted) with square brackets. Used for pretty printing in cases like debugging.
	 */
	fun expand() =
		buildString {
			append('[')
			for (range in ranges)
				for (char in range.start..range.end) append(char)
			append(']')
		}
}