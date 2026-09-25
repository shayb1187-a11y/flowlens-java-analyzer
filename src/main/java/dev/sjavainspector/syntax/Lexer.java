package dev.sjavainspector.syntax;

import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.api.SourcePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static dev.sjavainspector.syntax.Token.Kind.*;

/** One scanner per invocation; quotes and comments are handled before delimiters. */
public final class Lexer {
    private static final Map<String, Token.Kind> KEYWORDS = Map.ofEntries(
            Map.entry("int", INT), Map.entry("double", DOUBLE), Map.entry("boolean", BOOLEAN),
            Map.entry("char", CHAR), Map.entry("String", STRING), Map.entry("final", FINAL),
            Map.entry("void", VOID), Map.entry("if", IF), Map.entry("else", ELSE),
            Map.entry("while", WHILE), Map.entry("return", RETURN),
            Map.entry("true", TRUE), Map.entry("false", FALSE));
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private final List<Diagnostic> diagnostics;
    private int offset;
    private int line = 1;
    private int column = 1;

    public Lexer(String source, List<Diagnostic> diagnostics) {
        this.source = source;
        this.diagnostics = diagnostics;
    }

    public List<Token> scan() {
        while (!end()) {
            SourcePosition start = position();
            char c = advance();
            switch (c) {
                case ' ', '\t', '\n', '\r', '\f' -> { }
                case '(' -> add(LEFT_PAREN, start);
                case ')' -> add(RIGHT_PAREN, start);
                case '{' -> add(LEFT_BRACE, start);
                case '}' -> add(RIGHT_BRACE, start);
                case ',' -> add(COMMA, start);
                case ';' -> add(SEMICOLON, start);
                case '=' -> add(EQUAL, start);
                case '!' -> add(NOT, start);
                case '&' -> paired('&', AND, start);
                case '|' -> paired('|', OR, start);
                case '/', '*' -> comment(c, start);
                case '"', '\'' -> quoted(c, start);
                default -> {
                    if (letter(c) || c == '_') identifier(start);
                    else if (digit(c) || c == '.' || c == '+' || c == '-') number(start);
                    else error("Unexpected character '" + c + "'.", start);
                }
            }
        }
        tokens.add(new Token(EOF, "", position()));
        return List.copyOf(tokens);
    }

    private void identifier(SourcePosition start) {
        while (letter(peek()) || digit(peek()) || peek() == '_') advance();
        String word = source.substring(start.offset(), offset);
        add(KEYWORDS.getOrDefault(word, IDENTIFIER), start);
    }

    private void number(SourcePosition start) {
        // Signs belong to numeric literals. Arithmetic operators are outside this language.
        while (digit(peek()) || peek() == '.') advance();
        String number = source.substring(start.offset(), offset);
        if (number.matches("[+-]?[0-9]+")) add(INT_LITERAL, start);
        else if (number.matches("[+-]?(?:[0-9]+\\.[0-9]*|\\.[0-9]+)")) add(DOUBLE_LITERAL, start);
        else error("Malformed numeric literal '" + number + "'.", start);
    }

    private void quoted(char quote, SourcePosition start) {
        int characters = 0;
        boolean valid = true;
        while (!end() && peek() != quote && peek() != '\n' && peek() != '\r') {
            char c = advance();
            if (c == '\\') {
                if (end() || peek() == '\n' || peek() == '\r') break;
                char escaped = advance();
                if ("nrt\\\"'".indexOf(escaped) < 0) {
                    error("Unsupported escape sequence '\\" + escaped + "'.", start);
                    valid = false;
                }
            }
            characters++;
        }
        if (end() || peek() != quote) {
            error("Unterminated quoted literal.", start);
            return;
        }
        advance();
        if (quote == '\'' && characters != 1) {
            error("A char literal must contain exactly one character or escape.", start);
            valid = false;
        }
        if (valid) add(quote == '"' ? STRING_LITERAL : CHAR_LITERAL, start);
    }

    private void comment(char first, SourcePosition start) {
        if (first == '/' && match('/')) {
            while (!end() && peek() != '\n' && peek() != '\r') advance();
        } else if (first == '/' && match('*')) {
            while (!end() && !(peek() == '*' && peekNext() == '/')) advance();
            if (end()) error("Unterminated block comment.", start);
            else { advance(); advance(); }
        } else error("Unexpected character '" + first + "'.", start);
    }

    private void paired(char expected, Token.Kind kind, SourcePosition start) {
        if (match(expected)) add(kind, start);
        else error("Expected '" + expected + expected + "'.", start);
    }
    private boolean match(char c) {
        if (peek() != c || end()) return false;
        advance();
        return true;
    }
    private void add(Token.Kind kind, SourcePosition start) {
        tokens.add(new Token(kind, source.substring(start.offset(), offset), start));
    }
    private void error(String message, SourcePosition start) {
        diagnostics.add(Diagnostic.error("E001", message, start));
    }
    private char advance() {
        char c = source.charAt(offset++);
        if (c == '\r') { line++; column = 1; }
        else if (c == '\n') {
            if (offset < 2 || source.charAt(offset - 2) != '\r') line++;
            column = 1;
        } else column++;
        return c;
    }
    private char peek() { return end() ? '\0' : source.charAt(offset); }
    private char peekNext() { return offset + 1 >= source.length() ? '\0' : source.charAt(offset + 1); }
    private boolean end() { return offset >= source.length(); }
    private SourcePosition position() { return new SourcePosition(line, column, offset); }
    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
    private static boolean letter(char c) { return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'; }
}
