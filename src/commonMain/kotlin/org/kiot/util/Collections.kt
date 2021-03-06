package org.kiot.util

fun <T> Iterable<T>.contentEquals(other: Iterable<T>): Boolean {
	val iteratorA = iterator()
	val iteratorB = other.iterator()
	while (iteratorA.hasNext() && iteratorB.hasNext())
		if (iteratorA.next() != iteratorB.next()) return false
	return iteratorA.hasNext() == iteratorB.hasNext()
}

fun <T> MutableList<T>.swap(x: Int, y: Int) {
	val tmp = this[x]
	this[x] = this[y]
	this[y] = tmp
}