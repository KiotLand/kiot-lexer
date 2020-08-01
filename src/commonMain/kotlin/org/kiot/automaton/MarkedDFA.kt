package org.kiot.automaton

import org.kiot.util.Binarizable
import org.kiot.util.Binarizer
import org.kiot.util.Binary
import org.kiot.util.binarySize

interface Mark {
	fun merge(other: Mark): Mark

	fun canMerge(other: Mark): Boolean

	val action: Int
}

@Suppress("MemberVisibilityCanBePrivate")
class MarksConflictException(val firstMark: Mark, val secondMark: Mark, val pattern: List<PlainCharRange>? = null) :
	RuntimeException() {
	override val message: String?
		get() = "$firstMark conflicts with $secondMark${pattern.let {
			if (it == null) ""
			else " under this pattern: ${it.joinToString("")}"
		}}"
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Mark> mergeMark(a: T?, b: T?): T? {
	if (a == null || b == null) return a ?: b
	if (a.canMerge(b)) return a.merge(b) as T?
	throw MarksConflictException(a, b)
}

class ActionMark(override val action: Int, var name: String = action.toString()) : Mark {
	init {
		require(action != 0) { "Action 0 is reserved." }
	}

	override fun merge(other: Mark): Mark = this

	override fun canMerge(other: Mark): Boolean {
		if (other !is ActionMark) return false
		return action == other.action && name == other.name
	}

	override fun toString(): String = "FunctionMark($name)"

	infix fun withName(name: String) {
		this.name = name
	}
}

class PriorityMark<T : Mark>(val priority: Int, val mark: T) : Mark {
	override fun merge(other: Mark): Mark {
		other as PriorityMark<*>
		return if (priority < other.priority) this
		else other
	}

	override fun canMerge(other: Mark): Boolean = other is PriorityMark<*>

	override val action: Int
		get() = mark.action

	override fun toString(): String = "PriorityMark($priority, $mark)"
}

sealed class MarkedDFA<D : DFA>(val dfa: D) : Binarizable {
	companion object {
		val binarizer = object : Binarizer<MarkedDFA<*>> {
			override fun binarize(bin: Binary, value: MarkedDFA<*>) = bin.run {
				when (value) {
					is MarkedGeneralDFA -> {
						put(false)
						put(value.dfa, GeneralDFA.binarizer)
						putList(value.actions)
					}
					is MarkedCompressedDFA -> {
						put(true)
						put(value.dfa, CompressedDFA.binarizer)
						put(value.actions)
					}
				}
			}

			override fun debinarize(bin: Binary): MarkedDFA<*> =
				if (bin.boolean()) {
					MarkedCompressedDFA(
						bin.read(),
						bin.intArray()
					)
				} else {
					MarkedGeneralDFA(
						bin.read(),
						bin.readList()
					)
				}

			override fun measure(value: MarkedDFA<*>): Int =
				Boolean.binarySize +
						(when (value) {
							is MarkedGeneralDFA -> value.dfa.binarySize + Binary.measureList(value.actions)
							is MarkedCompressedDFA -> value.dfa.binarySize + value.actions.binarySize
						})
		}.also { Binary.register(it) }
	}

	abstract fun action(cellIndex: Int, transitionIndex: Int): Int
}

class MarkedGeneralDFA(dfa: GeneralDFA, internal val actions: List<IntArray>) : MarkedDFA<GeneralDFA>(dfa) {
	override fun action(cellIndex: Int, transitionIndex: Int) = actions[cellIndex][transitionIndex]

	fun compressed(): MarkedCompressedDFA {
		val newDFA = dfa.compressed()
		val newMarks = IntArray(newDFA.transitionSize)
		var tot = 0
		for (cell in dfa.indices)
			actions[cell].let {
				it.copyInto(newMarks, tot)
				tot += it.size
			}
		return MarkedCompressedDFA(newDFA, newMarks)
	}
}

class MarkedCompressedDFA(
	dfa: CompressedDFA,
	internal val actions: IntArray
) : MarkedDFA<CompressedDFA>(dfa) {
	override fun action(cellIndex: Int, transitionIndex: Int) = actions[dfa.transition(cellIndex, transitionIndex)]
}