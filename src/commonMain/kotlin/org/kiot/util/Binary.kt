@file:Suppress("NOTHING_TO_INLINE")

package org.kiot.util

class Binary(val array: ByteArray, var index: Int = 0, val endIndex: Int = array.size) {
	inline fun <R> require(count: Int, block: () -> R): R {
		require(index + count < endIndex) { "requires $count bytes" }
		return block()
	}

	inline fun boolean(): Boolean = byte() != 0.toByte()
	inline fun char(): Char = short().toChar()

	inline fun byte(): Byte = require(1) { array[index++] }
	inline fun short(): Short = require(2) {
		((byte().toInt() shl 8) or byte().toInt()).toShort()
	}

	fun int(): Int = require(4) {
		(byte().toInt() shl 24) or
				(byte().toInt() shl 16) or
				(byte().toInt() shl 8) or
				byte().toInt()
	}

	fun long(): Long = require(8) {
		(byte().toLong() shl 56) or
				(byte().toLong() shl 48) or
				(byte().toLong() shl 40) or
				(byte().toLong() shl 32) or
				(byte().toLong() shl 24) or
				(byte().toLong() shl 16) or
				(byte().toLong() shl 8) or
				byte().toLong()
	}

	inline fun put(value: Boolean) = require(1) {
		put(if (value) 1.toByte() else 0.toByte())
	}

	inline fun put(value: Char) = put(value.toShort())

	inline fun put(value: Byte) = require(1) { array[index++] = value }
	inline fun put(value: Short) = require(2) {
		val int = value.toInt()
		put((int ushr 8).toByte())
		put(int.toByte())
	}

	fun put(value: Int) = require(4) {
		put((value ushr 24).toByte())
		put((value ushr 16).toByte())
		put((value ushr 8).toByte())
		put(value.toByte())
	}

	fun put(value: Long) = require(8) {
		put((value ushr 56).toByte())
		put((value ushr 48).toByte())
		put((value ushr 40).toByte())
		put((value ushr 32).toByte())
		put((value ushr 24).toByte())
		put((value ushr 16).toByte())
		put((value ushr 8).toByte())
		put(value.toByte())
	}
}

inline fun ByteArray.asBinary(index: Int = 0, endIndex: Int = size) = Binary(this, index, endIndex)
