package org.kiot.automata

import org.kiot.util.charListOf

open class RegExpException(override val message: String? = null) : RuntimeException()

@Suppress("NOTHING_TO_INLINE")
private inline fun Char.description() = "$this (0x${toInt().toString(16)})"

private class IllegalEscapeException(char: Char) :
	RegExpException("Illegal escape char: ${char.description()}")

private class UnexpectedCharException(char: Char) :
	RegExpException("Unexpected char: ${char.description()}")

/**
 * Simple RegExp parser.
 *
 * @author Mivik
 */
@Suppress("NOTHING_TO_INLINE")
internal class RegExpParser(private val elements: List<Any> /* could be String or NFABuilder */) {
	companion object {
		const val LEGAL_ESCAPE_CHAR = "-()*+.[]?\\^{}|"
	}

	private var index = 0
	private var stringIndex = initialStringIndex()
	private var buffer: Char? = null

	private fun eof() = index == elements.size
	private fun initialStringIndex() = when (elements[index]) {
		is NFA -> -1
		else -> 0
	}

	private fun moveForward() {
		if (buffer != null) {
			buffer = null
			return
		}
		if (stringIndex >= 0 && ++stringIndex != (elements[index] as String).length) return
		stringIndex =
			if (++index == elements.size) -2
			else initialStringIndex()
	}

	private inline fun unget(char: Char) {
		require(buffer == null) { "unget conflict" }
		buffer = char
	}

	private inline fun view(expBlock: (NFA) -> Unit, charBlock: (Char) -> Unit) {
		require(!eof()) { "Unexpected termination" }
		buffer?.let {
			charBlock(it)
			return
		}
		when (stringIndex) {
			-1 -> expBlock(elements[index] as NFA)
			else -> charBlock((elements[index] as String)[stringIndex])
		}
	}

	private inline fun viewChar(): Char {
		view({ error("Expected char") }) { return it }
		error()
	}

	private inline fun take(expBlock: (NFA) -> Unit, charBlock: (Char) -> Unit) {
		view({
			moveForward()
			expBlock(it)
		}) {
			moveForward()
			charBlock(it)
		}
	}

	private inline fun takeChar(): Char = viewChar().also { moveForward() }

	private inline fun error(message: String? = null): Nothing = throw RegExpException(message)
	private inline fun unexpected(char: Char): Nothing = throw UnexpectedCharException(char)
	private inline fun check() {
		if (eof()) error("Unexpected termination")
	}

	private inline fun expect(actual: Char, expected: Char) {
		if (actual != expected) error("Expected $expected, got ${actual.description()}")
	}

	private inline fun readChar(): Char {
		val char = takeChar()
		if (char == '\\') {
			view({ return char }) {
				if (it !in LEGAL_ESCAPE_CHAR) throw IllegalEscapeException(it)
				moveForward()
				return it
			}
			error("Unreachable")
		} else return char
	}

	private fun readInt(): Int? {
		if (eof() || viewChar() !in '0'..'9') return null
		var ret = 0
		while (!eof()) {
			val char = viewChar()
			if (char in '0'..'9') ret = ret * 10 + (char - '0')
			else return ret
			moveForward()
		}
		return ret
	}

	private fun tryReadCharClass(): CharClass? {
		return when (viewChar()) {
			'\\' -> {
				moveForward()
				check()
				view({ return null }) {
					return when (it.toLowerCase()) {
						'w' -> CharClass.letter
						'd' -> CharClass.digit
						's' -> CharClass.blank
						else -> {
							unget('\\')
							return null
						}
					}.let { charClass ->
						moveForward()
						if (it in 'A'..'Z') charClass.inverse() else charClass
					}
				}
				error("Unreachable")
			}
			'.' -> CharClass.any.also { moveForward() }
			else -> null
		}
	}

	private fun readCharClass(): CharClass {
		moveForward()
		check()
		val inverse = viewChar() == '^'
		if (inverse) moveForward()
		var lastChar: Char? = null
		val readChars = charListOf()
		val readRanges = mutableListOf<PlainCharRange>()
		loop@ while (true) {
			check()
			when (viewChar()) {
				'^' -> unexpected('^')
				'-' -> {
					moveForward()
					if (lastChar == null) {
						readChars += '-'
						lastChar = '-'
					} else {
						check()
						readChars.removeAt(readChars.lastIndex)
						val endChar = readChar()
						if (lastChar > endChar) error("Illegal char range: ${lastChar.description()} to ${endChar.description()}")
						readRanges += lastChar plainTo endChar
					}
				}
				']' -> break@loop
				else -> {
					val charClass = tryReadCharClass()
					if (charClass == null) readChars += readChar().also { lastChar = it }
					else readRanges.addAll(charClass.ranges)
				}
			}
		}
		moveForward()
		var ret = CharClass.empty
		for (range in readRanges) ret = ret.merge(CharClass(range))
		readChars.sort()
		if (readChars.isNotEmpty()) {
			var tot = 1
			for (i in 1 until readChars.size) if (readChars[i] != readChars[tot - 1]) readChars[tot++] = readChars[i]
			readChars.resize(tot)
			ret = ret.merge(CharClass.from(*readChars.toCharArray()))
		}
		return if (inverse) ret.inverse() else ret
	}

	fun readExpression(): NFA {
		moveForward()
		check()
		val orList = mutableListOf<NFA>()
		var tmp = NFA()
		var last: NFA? = null
		var lastIsChar = false
		fun commit() {
			last?.let { tmp.append(it) }
			lastIsChar = false
			last = null
		}

		loop@ while (true) {
			check()
			take({
				commit()
				last = it
			}) {
				when (it) {
					'(' -> {
						unget(it)
						commit()
						last = readExpression()
					}
					')' -> {
						commit()
						if (tmp.isNotEmpty()) orList += tmp
						if (orList.size == 1) return orList[0]
						return NFA.branch(orList)
					}
					'[' -> {
						unget(it)
						commit()
						last = NFA.from(readCharClass())
					}
					'+' -> last!!.oneOrMore().also { commit() }
					'*' -> last!!.any().also { commit() }
					'?' -> last!!.unnecessary().also { commit() }
					'|' -> {
						commit()
						if (tmp.isEmpty()) unexpected('|')
						orList += tmp
						tmp = NFA()
					}
					'{' -> {
						val atLeast = readInt() ?: error("Expected integer")
						check()
						when (val char = takeChar()) {
							',' -> {
								check()
								val atMost = readInt()
								expect(takeChar(), '}')
								if (atMost == null) last!!.repeatAtLeast(atLeast)
								else last!!.repeat(atLeast, atMost)
								commit()
							}
							'}' -> {
								last!!.repeat(atLeast, atLeast)
								commit()
							}
							else -> unexpected(char)
						}
					}
					else -> {
						unget(it)
						val charClass = tryReadCharClass()
						if (charClass == null) {
							if (!lastIsChar) {
								commit()
								lastIsChar = true
								last = NFA()
							}
							last!!.append(readChar())
						} else {
							commit()
							last = NFA.from(charClass)
						}
					}
				}
			}
		}
	}
}

fun Char.regexp(): NFA = RegExpParser(listOf("($this)")).readExpression()
fun String.regexp(): NFA = RegExpParser(listOf("($this)")).readExpression()

/**
 * Allow using plus operator to concat [NFA] and [String].
 *
 * As the name mentioned, it's temporary and will change after calling plus
 * operator on it. Note that the content of a [TemporaryRegExpBuilder] will be
 * cleared after calling [build].
 */
class TemporaryRegExpBuilder {
	private val list: MutableList<Any> = mutableListOf("(")

	operator fun plus(string: String): TemporaryRegExpBuilder = apply {
		list += string
	}

	operator fun plus(nfa: NFA): TemporaryRegExpBuilder = apply {
		list += nfa
	}

	operator fun plus(other: TemporaryRegExpBuilder): TemporaryRegExpBuilder = apply {
		list.addAll(other.list)
	}

	fun build(): NFA {
		list += ")"
		return RegExpParser(list).readExpression().also { list.clear() }
	}
}

val RegExp: TemporaryRegExpBuilder
	get() = TemporaryRegExpBuilder()
