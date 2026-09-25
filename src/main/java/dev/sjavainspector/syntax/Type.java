package dev.sjavainspector.syntax;

/** Language types, not Java runtime classes. ERROR suppresses cascading type errors. */
public enum Type {
    INT("int"), DOUBLE("double"), BOOLEAN("boolean"), CHAR("char"), STRING("String"), ERROR("<error>");
    private final String display;
    Type(String display) { this.display = display; }
    public String display() { return display; }
}
