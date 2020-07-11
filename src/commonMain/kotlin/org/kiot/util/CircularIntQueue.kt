package org.kiot.util

/**
 * A circular int queue that has a fixed size.
 *
 * @author Mivik
 */
class CircularIntQueue(private val maximumSize: Int) {
	private val elements = IntArray(maximumSize)
	private var head = 0
	private var tail = 0
	private var full = false

	fun clear() {
		head = 0
		tail = 0
		full = false
	}

	fun push(element: Int) {
		if (full) error("Out of bound.")
		elements[tail] = element
		if (++tail == maximumSize) tail = 0
		if (tail == head) full = true
	}

	fun pop(): Int =
		elements[head].also {
			if (head == tail) full = false
			if (++head == maximumSize) head = 0
		}

	fun front(): Int = elements[head]

	val size: Int
		get() = if (full) maximumSize else if (tail >= head) (tail - head) else (head - tail + maximumSize)

	fun isEmpty(): Boolean = (!full) && head == tail
	fun isNotEmpty(): Boolean = full || head != tail
}