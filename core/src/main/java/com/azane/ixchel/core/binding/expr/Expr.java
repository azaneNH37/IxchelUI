package com.azane.ixchel.core.binding.expr;

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
public sealed interface Expr {
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

        public String text() {
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
        public final int at;

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
}
