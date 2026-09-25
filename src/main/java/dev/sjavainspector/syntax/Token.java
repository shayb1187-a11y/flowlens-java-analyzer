package dev.sjavainspector.syntax;

import dev.sjavainspector.api.SourcePosition;

public record Token(Kind kind, String text, SourcePosition position) {
    public enum Kind {
        IDENTIFIER, INT_LITERAL, DOUBLE_LITERAL, STRING_LITERAL, CHAR_LITERAL,
        INT, DOUBLE, BOOLEAN, CHAR, STRING, FINAL, VOID, IF, ELSE, WHILE, RETURN, TRUE, FALSE,
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, COMMA, SEMICOLON, EQUAL, AND, OR, NOT, EOF
    }
}
