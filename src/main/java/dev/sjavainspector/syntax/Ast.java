package dev.sjavainspector.syntax;

import dev.sjavainspector.api.SourcePosition;
import java.util.List;
import java.util.Optional;

/** Immutable syntax tree. Lists are defensively copied at every ownership boundary. */
public final class Ast {
    private Ast() { }

    public sealed interface Node permits Program, Method, Block, Statement, Expression {
        SourcePosition position();
        <R> R accept(Visitor<R> visitor);
    }
    public sealed interface Statement extends Node permits Declaration, Assignment, Call, If, While, Return { }
    public sealed interface Expression extends Node permits Literal, Variable, Logical, Not { }

    public record Program(List<Statement> globals, List<Method> methods, SourcePosition position) implements Node {
        public Program { globals = List.copyOf(globals); methods = List.copyOf(methods); }
        public <R> R accept(Visitor<R> v) { return v.visitProgram(this); }
    }
    /** {@code position} is the {@code void} keyword; {@code namePosition} is the method-name token. */
    public record Method(String name, List<Parameter> parameters, Block body, SourcePosition position,
                         SourcePosition namePosition) implements Node {
        public Method { parameters = List.copyOf(parameters); }
        /** Compatibility constructor for callers without a separate name token. */
        public Method(String name, List<Parameter> parameters, Block body, SourcePosition position) {
            this(name, parameters, body, position, position);
        }
        public <R> R accept(Visitor<R> v) { return v.visitMethod(this); }
    }
    public record Parameter(Type type, String name, boolean isFinal, SourcePosition position) { }
    public record Block(List<Statement> statements, SourcePosition position) implements Node {
        public Block { statements = List.copyOf(statements); }
        public <R> R accept(Visitor<R> v) { return v.visitBlock(this); }
    }
    public record Binding(String name, Optional<Expression> initializer, SourcePosition position) { }
    public record Declaration(Type type, boolean isFinal, List<Binding> bindings,
                              SourcePosition position) implements Statement {
        public Declaration { bindings = List.copyOf(bindings); }
        public <R> R accept(Visitor<R> v) { return v.visitDeclaration(this); }
    }
    public record Write(String name, Expression value, SourcePosition position) { }
    public record Assignment(List<Write> writes, SourcePosition position) implements Statement {
        public Assignment { writes = List.copyOf(writes); }
        public <R> R accept(Visitor<R> v) { return v.visitAssignment(this); }
    }
    public record Call(String name, List<Expression> arguments, SourcePosition position) implements Statement {
        public Call { arguments = List.copyOf(arguments); }
        public <R> R accept(Visitor<R> v) { return v.visitCall(this); }
    }
    public record If(Expression condition, Block thenBranch, Optional<Block> elseBranch,
                     SourcePosition position) implements Statement {
        public <R> R accept(Visitor<R> v) { return v.visitIf(this); }
    }
    public record While(Expression condition, Block body, SourcePosition position) implements Statement {
        public <R> R accept(Visitor<R> v) { return v.visitWhile(this); }
    }
    public record Return(SourcePosition position) implements Statement {
        public <R> R accept(Visitor<R> v) { return v.visitReturn(this); }
    }
    public record Literal(Type type, String spelling, SourcePosition position) implements Expression {
        public <R> R accept(Visitor<R> v) { return v.visitLiteral(this); }
    }
    public record Variable(String name, SourcePosition position) implements Expression {
        public <R> R accept(Visitor<R> v) { return v.visitVariable(this); }
    }
    public enum LogicalOperator { AND, OR }
    public record Logical(Expression left, LogicalOperator operator, Expression right,
                          SourcePosition position) implements Expression {
        public <R> R accept(Visitor<R> v) { return v.visitLogical(this); }
    }
    public record Not(Expression operand, SourcePosition position) implements Expression {
        public <R> R accept(Visitor<R> v) { return v.visitNot(this); }
    }

    /** Double dispatch lets analysis operations evolve without behavior in data nodes. */
    public interface Visitor<R> {
        R visitProgram(Program node);
        R visitMethod(Method node);
        R visitBlock(Block node);
        R visitDeclaration(Declaration node);
        R visitAssignment(Assignment node);
        R visitCall(Call node);
        R visitIf(If node);
        R visitWhile(While node);
        R visitReturn(Return node);
        R visitLiteral(Literal node);
        R visitVariable(Variable node);
        R visitLogical(Logical node);
        R visitNot(Not node);
    }
}
