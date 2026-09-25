package dev.sjavainspector.semantic;

import dev.sjavainspector.syntax.Type;

/** Central policy for this s-Java dialect, including its numeric-to-boolean conversion. */
public final class TypeRules {
    public boolean canAssign(Type target, Type source) {
        return target == Type.ERROR || source == Type.ERROR || target == source
                || target == Type.DOUBLE && source == Type.INT
                || target == Type.BOOLEAN && (source == Type.INT || source == Type.DOUBLE);
    }
    public boolean canTest(Type type) {
        return type == Type.BOOLEAN || type == Type.INT || type == Type.DOUBLE || type == Type.ERROR;
    }
}
