package org.kiot.util

/**
 * A circular int queue that has a fixed size.
 *
 * @author Mivik
 */
class CircularIntQueue(private val S: Int) {
	private val elements = IntArray(S)
	private var head = 0
	private var tail = 0
	private var full = false

	fun clear() {
		head = 0
		tail = 0
		full = false
	}

	fun push(element: Int) {
		elements[tail] = element
		if (++tail == S) tail = 0
		if (tail == head) full = true
	}

	fun pop(): Int =
		elements[head].also {
			if (head == tail) full = false
			if (++head == S) head = 0
		}

	fun front(): Int = elements[head]

	val size: Int
		get() = if (full) S else if (tail >= head) (tail - head) else (head - tail + S)

	fun isEmpty(): Boolean = (!full) && head == tail
	fun isNotEmpty(): Boolean = full || head != tail
}