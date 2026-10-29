package com.azane.ixchel.core.binding;

import java.util.ArrayList;
import java.util.List;

/**
 * An expression of a directive value, parsed but not yet resolved. {@code at} is the offset of the
 * node's first character (of the operator for unary and binary nodes) in the directive value.
 *
 * <pre>
 * expr    := or ('?' expr ':' expr)?
 * or      := and ('||' and)*
 * and     := eq ('&amp;&amp;' eq)*
 * eq      := rel (('==' | '!=') rel)*
 * rel     := add (('&lt;' | '&lt;=' | '&gt;' | '&gt;=') add)?
 * add     := mul (('+' | '-') mul)*
 * mul     := unary (('*' | '/' | '%') unary)*
 * unary   := ('!' | '-') unary | primary
 * primary := number | 'string' | true | false | path | '(' expr ')'
 * path    := name ('.' name)*
 * stmts   := stmt (';' stmt)* ';'?
 * stmt    := path '=' expr | name ('(' expr ')')?
 * </pre>
 */
sealed interface Expr {
    int at();

    /** An {@code Integer}, {@code Double}, {@code String} or {@code Boolean}. */
    record Literal(Object value, int at) implements Expr {
    }

    /** {@code names.get(i)} starts at {@code offsets.get(i)}. */
    record Path(List<String> names, List<Integer> offsets) implements Expr {
        @Override
        public int at() {
            return offsets.get(0);
        }

        String text() {
            return String.join(".", names);
        }
    }

    record Unary(String op, Expr operand, int at) implements Expr {
    }

    record Binary(String op, Expr left, Expr right, int at) implements Expr {
    }

    record Conditional(Expr test, Expr then, Expr otherwise, int at) implements Expr {
    }

    /** A statement of an {@code on:} value. */
    sealed interface Stmt {
        int at();
    }

    /** {@code action} or {@code action(arg)}; {@code arg} is {@code null} without parentheses. */
    record Call(String action, Expr arg, int at) implements Stmt {
    }

    record Assign(Path target, Expr value, int at) implements Stmt {
    }

    /** A malformed value; {@code at} is the offset of the offending character. */
    final class SyntaxError extends Exception {
        final int at;

        SyntaxError(int at, String message) {
            super(message, null, false, false);
            this.at = at;
        }
    }

    static Expr parseExpression(String src) throws SyntaxError {
        Parser p = new Parser(src);
        Expr e = p.expr();
        p.expectEnd();
        return e;
    }

    static List<Stmt> parseStatements(String src) throws SyntaxError {
        Parser p = new Parser(src);
        List<Stmt> out = new ArrayList<>();
        do {
            if (p.atEnd()) {
                break;
            }
            out.add(p.stmt());
        } while (p.accept(";"));
        p.expectEnd();
        if (out.isEmpty()) {
            throw new SyntaxError(0, "expected an action call or an assignment");
        }
        return out;
    }

    /** Recursive descent over a hand-rolled scanner; whitespace separates tokens. */
    final class Parser {
        private static final String[] OPERATORS = {
                "==", "!=", "<=", ">=", "&&", "||", "<", ">", "+", "-", "*", "/", "%", "!", "?", ":", "(", ")", ".", ";", "="};

        private final String src;
        private int i;

        Parser(String src) {
            this.src = src;
        }

        Expr expr() throws SyntaxError {
            Expr test = binary(0);
            skip();
            int at = i;
            if (!accept("?")) {
                return test;
            }
            Expr then = expr();
            expect(":");
            Expr otherwise = expr();
            return new Conditional(test, then, otherwise, at);
        }

        private static final String[][] LEVELS = {
                {"||"}, {"&&"}, {"==", "!="}, {"<=", ">=", "<", ">"}, {"+", "-"}, {"*", "/", "%"}};

        private Expr binary(int level) throws SyntaxError {
            if (level == LEVELS.length) {
                return unary();
            }
            Expr left = binary(level + 1);
            boolean relational = level == 3;
            while (true) {
                String op = operator(LEVELS[level]);
                if (op == null) {
                    return left;
                }
                int at = i;
                i += op.length();
                left = new Binary(op, left, binary(level + 1), at);
                if (relational && operator(LEVELS[level]) != null) {
                    throw new SyntaxError(i, "comparisons do not chain; combine them with &&");
                }
            }
        }

        private Expr unary() throws SyntaxError {
            skip();
            int at = i;
            if (peekOperator("!") || peekOperator("-")) {
                String op = src.substring(i, i + 1);
                i++;
                return new Unary(op, unary(), at);
            }
            return primary();
        }

        private Expr primary() throws SyntaxError {
            skip();
            int at = i;
            if (i >= src.length()) {
                throw new SyntaxError(i, "expected a value");
            }
            char c = src.charAt(i);
            if (c == '(') {
                i++;
                Expr e = expr();
                expect(")");
                return e;
            }
            if (c == '\'') {
                return string();
            }
            if (isDigit(c)) {
                return number();
            }
            if (Character.isJavaIdentifierStart(c)) {
                Path p = path();
                if (p.names().size() == 1 && (p.names().get(0).equals("true") || p.names().get(0).equals("false"))) {
                    return new Literal(Boolean.valueOf(p.names().get(0)), at);
                }
                return p;
            }
            throw new SyntaxError(i, "unexpected '" + c + "'");
        }

        Path path() throws SyntaxError {
            List<String> names = new ArrayList<>();
            List<Integer> offsets = new ArrayList<>();
            do {
                skip();
                offsets.add(i);
                names.add(name());
            } while (acceptDot());
            return new Path(List.copyOf(names), List.copyOf(offsets));
        }

        /** A {@code .} continuing a path; not the decimal point of a number. */
        private boolean acceptDot() {
            skip();
            if (i < src.length() && src.charAt(i) == '.') {
                i++;
                return true;
            }
            return false;
        }

        private String name() throws SyntaxError {
            int start = i;
            if (i >= src.length() || !Character.isJavaIdentifierStart(src.charAt(i))) {
                throw new SyntaxError(i, "expected a name");
            }
            i++;
            while (i < src.length() && Character.isJavaIdentifierPart(src.charAt(i))) {
                i++;
            }
            return src.substring(start, i);
        }

        private Expr number() throws SyntaxError {
            int start = i;
            while (i < src.length() && isDigit(src.charAt(i))) {
                i++;
            }
            boolean decimal = i + 1 < src.length() && src.charAt(i) == '.' && isDigit(src.charAt(i + 1));
            if (decimal) {
                i++;
                while (i < src.length() && isDigit(src.charAt(i))) {
                    i++;
                }
                return new Literal(Double.parseDouble(src.substring(start, i)), start);
            }
            try {
                return new Literal(Integer.parseInt(src.substring(start, i)), start);
            } catch (NumberFormatException ex) {
                throw new SyntaxError(start, "number too large for an Integer");
            }
        }

        private Expr string() throws SyntaxError {
            int start = i++;
            StringBuilder sb = new StringBuilder();
            while (i < src.length()) {
                char c = src.charAt(i++);
                if (c == '\'') {
                    return new Literal(sb.toString(), start);
                }
                if (c == '\\') {
                    if (i >= src.length() || (src.charAt(i) != '\'' && src.charAt(i) != '\\')) {
                        throw new SyntaxError(i - 1, "only \\' and \\\\ are escapes");
                    }
                    c = src.charAt(i++);
                }
                sb.append(c);
            }
            throw new SyntaxError(start, "unterminated string");
        }

        Stmt stmt() throws SyntaxError {
            skip();
            int at = i;
            Path target = path();
            if (peekOperator("=")) {
                i++;
                return new Assign(target, expr(), at);
            }
            if (target.names().size() > 1) {
                throw new SyntaxError(i, "expected '=' after '" + target.text() + "'");
            }
            Expr arg = null;
            if (accept("(")) {
                skip();
                if (i < src.length() && src.charAt(i) == ')') {
                    throw new SyntaxError(i, "an action without an argument takes no parentheses");
                }
                arg = expr();
                expect(")");
            }
            return new Call(target.names().get(0), arg, at);
        }

        boolean atEnd() {
            skip();
            return i >= src.length();
        }

        void expectEnd() throws SyntaxError {
            if (!atEnd()) {
                throw new SyntaxError(i, "unexpected '" + src.charAt(i) + "'");
            }
        }

        boolean accept(String op) {
            if (peekOperator(op)) {
                i += op.length();
                return true;
            }
            return false;
        }

        private void expect(String op) throws SyntaxError {
            if (!accept(op)) {
                throw new SyntaxError(i, "expected '" + op + "'");
            }
        }

        /** Whether the longest operator at the cursor is {@code op}, so {@code =} does not match {@code ==}. */
        private boolean peekOperator(String op) {
            skip();
            for (String o : OPERATORS) {
                if (src.startsWith(o, i)) {
                    return o.equals(op);
                }
            }
            return false;
        }

        private String operator(String[] ops) {
            for (String op : ops) {
                if (peekOperator(op)) {
                    return op;
                }
            }
            return null;
        }

        private void skip() {
            while (i < src.length() && Character.isWhitespace(src.charAt(i))) {
                i++;
            }
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }
}
