package dev.sjavainspector.syntax;

import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.api.SourcePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static dev.sjavainspector.syntax.Token.Kind.*;

/** Recursive-descent grammar with statement-boundary recovery and bounded recursion. */
public final class Parser {
    private static final int MAX_BLOCK_DEPTH = 64;
    private static final int MAX_EXPRESSION_PARTS = 128;
    private final List<Token> tokens;
    private final List<Diagnostic> diagnostics;
    private int current;
    private int blockDepth;
    private int expressionParts;

    public Parser(List<Token> tokens, List<Diagnostic> diagnostics) {
        this.tokens = tokens;
        this.diagnostics = diagnostics;
    }

    public Ast.Program parse() {
        List<Ast.Statement> globals = new ArrayList<>();
        List<Ast.Method> methods = new ArrayList<>();
        while (!check(EOF)) {
            int statementStart = current;
            try {
                if (match(VOID)) methods.add(method(previous()));
                else if (declarationStart()) globals.add(declaration());
                else if (check(IDENTIFIER)) globals.add(assignment());
                else throw error(peek(), "Expected a global declaration, assignment, or void method.");
            } catch (ParseFailure failure) {
                synchronize(statementStart);
                if (check(RIGHT_BRACE)) advance();
            }
        }
        return new Ast.Program(globals, methods, SourcePosition.start());
    }

    private Ast.Method method(Token start) {
        Token name = name(true);
        consume(LEFT_PAREN, "Expected '(' after method name.");
        List<Ast.Parameter> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                boolean isFinal = match(FINAL);
                Type type = type();
                Token parameter = name(false);
                parameters.add(new Ast.Parameter(type, parameter.text(), isFinal, parameter.position()));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expected ')' after parameters.");
        return new Ast.Method(name.text(), parameters, block(), start.position(), name.position());
    }

    private Ast.Block block() {
        Token start = consume(LEFT_BRACE, "Expected '{' to start a block.");
        if (blockDepth >= MAX_BLOCK_DEPTH) {
            throw error(start, "Block nesting exceeds the supported limit of " + MAX_BLOCK_DEPTH + ".");
        }
        blockDepth++;
        try {
            List<Ast.Statement> statements = new ArrayList<>();
            while (!check(RIGHT_BRACE) && !check(EOF)) {
                int statementStart = current;
                try { statements.add(statement()); }
                catch (ParseFailure failure) { synchronize(statementStart); }
            }
            consume(RIGHT_BRACE, "Expected '}' to close the block.");
            return new Ast.Block(statements, start.position());
        } finally { blockDepth--; }
    }

    private Ast.Statement statement() {
        if (declarationStart()) return declaration();
        if (match(RETURN)) {
            Token start = previous();
            consume(SEMICOLON, "A void return must be written 'return;'.");
            return new Ast.Return(start.position());
        }
        if (match(IF)) {
            Token start = previous();
            Ast.Expression condition = condition();
            Ast.Block thenBranch = block();
            Optional<Ast.Block> elseBranch = match(ELSE) ? Optional.of(block()) : Optional.empty();
            return new Ast.If(condition, thenBranch, elseBranch, start.position());
        }
        if (match(WHILE)) {
            Token start = previous();
            Ast.Expression condition = condition();
            return new Ast.While(condition, block(), start.position());
        }
        if (check(IDENTIFIER) && tokens.get(current + 1).kind() == LEFT_PAREN) return call();
        if (check(IDENTIFIER)) return assignment();
        throw error(peek(), "Expected a declaration, assignment, call, if, while, or return.");
    }

    private Ast.Declaration declaration() {
        Token start = peek();
        boolean isFinal = match(FINAL);
        Type type = type();
        List<Ast.Binding> bindings = new ArrayList<>();
        do {
            Token name = name(false);
            Optional<Ast.Expression> initializer = match(EQUAL) ? Optional.of(expression()) : Optional.empty();
            bindings.add(new Ast.Binding(name.text(), initializer, name.position()));
        } while (match(COMMA));
        consume(SEMICOLON, "Expected ';' after declaration.");
        return new Ast.Declaration(type, isFinal, bindings, start.position());
    }

    private Ast.Assignment assignment() {
        Token start = peek();
        List<Ast.Write> writes = new ArrayList<>();
        do {
            Token name = name(false);
            consume(EQUAL, "Expected '=' in assignment (calls are only allowed inside methods).");
            writes.add(new Ast.Write(name.text(), expression(), name.position()));
        } while (match(COMMA));
        consume(SEMICOLON, "Expected ';' after assignment.");
        return new Ast.Assignment(writes, start.position());
    }

    private Ast.Call call() {
        Token name = name(true);
        consume(LEFT_PAREN, "Expected '('.");
        List<Ast.Expression> args = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do { args.add(expression()); } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expected ')' after arguments.");
        consume(SEMICOLON, "Expected ';' after method call.");
        return new Ast.Call(name.text(), args, name.position());
    }

    private Ast.Expression condition() {
        consume(LEFT_PAREN, "Expected '(' before condition.");
        Ast.Expression result = expression();
        consume(RIGHT_PAREN, "Expected ')' after condition.");
        return result;
    }
    private Ast.Expression expression() { expressionParts = 0; return or(); }
    private Ast.Expression or() {
        Ast.Expression left = and();
        while (match(OR)) {
            Token operator = previous();
            expressionPart();
            left = new Ast.Logical(left, Ast.LogicalOperator.OR, and(), operator.position());
        }
        return left;
    }
    private Ast.Expression and() {
        Ast.Expression left = unary();
        while (match(AND)) {
            Token operator = previous();
            expressionPart();
            left = new Ast.Logical(left, Ast.LogicalOperator.AND, unary(), operator.position());
        }
        return left;
    }
    private Ast.Expression unary() {
        expressionPart();
        if (match(NOT)) {
            Token operator = previous();
            return new Ast.Not(unary(), operator.position());
        }
        if (match(LEFT_PAREN)) {
            Ast.Expression expression = or();
            consume(RIGHT_PAREN, "Expected ')' after expression.");
            return expression;
        }
        Token token = advance();
        return switch (token.kind()) {
            case INT_LITERAL -> new Ast.Literal(Type.INT, token.text(), token.position());
            case DOUBLE_LITERAL -> new Ast.Literal(Type.DOUBLE, token.text(), token.position());
            case STRING_LITERAL -> new Ast.Literal(Type.STRING, token.text(), token.position());
            case CHAR_LITERAL -> new Ast.Literal(Type.CHAR, token.text(), token.position());
            case TRUE, FALSE -> new Ast.Literal(Type.BOOLEAN, token.text(), token.position());
            case IDENTIFIER -> {
                validateName(token, false);
                yield new Ast.Variable(token.text(), token.position());
            }
            default -> throw error(token, "Expected a literal, variable, or logical expression.");
        };
    }

    private void expressionPart() {
        if (++expressionParts > MAX_EXPRESSION_PARTS) {
            throw error(peek(), "Expression exceeds the supported complexity limit of " + MAX_EXPRESSION_PARTS + ".");
        }
    }
    private Type type() {
        Token token = advance();
        return switch (token.kind()) {
            case INT -> Type.INT; case DOUBLE -> Type.DOUBLE; case BOOLEAN -> Type.BOOLEAN;
            case CHAR -> Type.CHAR; case STRING -> Type.STRING;
            default -> throw error(token, "Expected int, double, boolean, char, or String.");
        };
    }
    private boolean declarationStart() {
        return switch (peek().kind()) {
            case FINAL, INT, DOUBLE, BOOLEAN, CHAR, STRING -> true;
            default -> false;
        };
    }
    private Token name(boolean method) {
        Token token = consume(IDENTIFIER, "Expected an identifier (keywords cannot be names).");
        validateName(token, method);
        return token;
    }
    private void validateName(Token token, boolean method) {
        String pattern = method ? "[A-Za-z][A-Za-z0-9_]*" : "(?:[A-Za-z]|_[A-Za-z0-9])[A-Za-z0-9_]*";
        if (!token.text().matches(pattern)) throw error(token, "Invalid " + (method ? "method" : "variable") + " name.");
    }
    private void synchronize(int statementStart) {
        // The failed expression may already have consumed its semicolon. Do not
        // discard the following statement, and never return without making progress.
        if (current > statementStart && previous().kind() == SEMICOLON) return;
        while (!check(EOF) && !check(RIGHT_BRACE)) {
            if (advance().kind() == SEMICOLON) return;
        }
    }
    private Token consume(Token.Kind kind, String message) {
        if (check(kind)) return advance();
        throw error(peek(), message);
    }
    private boolean match(Token.Kind kind) { if (!check(kind)) return false; advance(); return true; }
    private boolean check(Token.Kind kind) { return peek().kind() == kind; }
    private Token advance() { Token token = peek(); if (token.kind() != EOF) current++; return token; }
    private Token peek() { return tokens.get(current); }
    private Token previous() { return tokens.get(current - 1); }
    private ParseFailure error(Token token, String message) {
        diagnostics.add(Diagnostic.error("E002", message, token.position()));
        return new ParseFailure();
    }
    /** Internal recovery signal only; invalid source is returned as data at the API boundary. */
    private static final class ParseFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        ParseFailure() { super(null, null, false, false); }
    }
}
