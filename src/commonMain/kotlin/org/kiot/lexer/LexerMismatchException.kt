package org.kiot.lexer

class LexerMismatchException(val startIndex: Int, val endIndex: Int): RuntimeException() {
	override val message: String?
		get() = "Mismatch in [$startIndex, $endIndex]"
}