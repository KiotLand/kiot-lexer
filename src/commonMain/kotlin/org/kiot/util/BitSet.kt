package org.kiot.util

/**
 * Fixed-length multi-platform bitset.
 */
class BitSet private constructor(private val length: Int, private val words: LongArray) {
	companion object {
		private fun Int.wordIndex(): Int = this shr 6
	}

	init {
		require(length >= 0) { "The length must be positive or zero" }
	}

	constructor(length: Int) : this(length, LongArray((length - 1).wordIndex() + 1))

	private fun Int.ensureInRange() {
		if (this < 0 || this >= length) throw IndexOutOfBoundsException()
	}

	val size: Int
		get() = length

	fun copy(): BitSet = BitSet(length, words.copyOf())

	operator fun get(index: Int): Boolean {
		index.ensureInRange()
		return (words[index.wordIndex()] and (1L shl index)) != 0L
	}

	operator fun set(index: Int, flag: Boolean) =
		if (flag) set(index) else clear(index)

	fun clear(index: Int) {
		index.ensureInRange()
		index.wordIndex().let {
			words[it] = words[it] and (1L shl index).inv()
		}
	}

	fun set(index: Int) {
		index.ensureInRange()
		index.wordIndex().let { words[it] = words[it] or (1L shl index) }
	}

	fun clear() {
		words.fill(0)
	}

	override fun hashCode(): Int {
		var h = 1234L
		for (i in words.indices.reversed())
			h = h xor (words[i] * (i + 1))
		return ((h shr 32) xor h).toInt()
	}

	override fun equals(other: Any?): Boolean =
		if (other is BitSet) length == other.length && words.contentEquals(other.words)
		else false
}