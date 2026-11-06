package com.azane.ixchel.doclet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Declarations as Java source writes them, with simple type names: what a page shows in its code blocks. */
final class Signatures {
    private static final List<Modifier> ORDER = List.of(Modifier.PUBLIC, Modifier.PROTECTED, Modifier.ABSTRACT,
            Modifier.DEFAULT, Modifier.STATIC, Modifier.SEALED, Modifier.NON_SEALED, Modifier.FINAL);

    private final Types types;

    Signatures(Types types) {
        this.types = types;
    }

    /** {@code t} with each class by its simple name, nested ones by their enclosing ones' too ({@code Map.Entry}). */
    String type(TypeMirror t) {
        if (t.getKind() == TypeKind.DECLARED) {
            DeclaredType d = (DeclaredType) t;
            String name = nestedName((TypeElement) d.asElement());
            if (d.getTypeArguments().isEmpty()) {
                return name;
            }
            return name + "<" + d.getTypeArguments().stream().map(this::type).collect(Collectors.joining(", ")) + ">";
        }
        if (t.getKind() == TypeKind.ARRAY) {
            return type(((ArrayType) t).getComponentType()) + "[]";
        }
        if (t.getKind() == TypeKind.TYPEVAR) {
            return ((TypeVariable) t).asElement().getSimpleName().toString();
        }
        if (t.getKind() == TypeKind.WILDCARD) {
            WildcardType w = (WildcardType) t;
            if (w.getExtendsBound() != null) {
                return "? extends " + type(w.getExtendsBound());
            }
            return w.getSuperBound() != null ? "? super " + type(w.getSuperBound()) : "?";
        }
        return t.toString();
    }

    /** {@code Outer.Inner} for a nested type, the simple name for a top-level one. */
    static String nestedName(TypeElement t) {
        StringBuilder sb = new StringBuilder(t.getSimpleName());
        for (Element e = t.getEnclosingElement(); e instanceof TypeElement o; e = o.getEnclosingElement()) {
            sb.insert(0, o.getSimpleName() + ".");
        }
        return sb.toString();
    }

    /** {@code public abstract sealed class Cell<T> permits Value, Param}, the header of {@code t}'s declaration. */
    String typeHeader(TypeElement t) {
        StringBuilder sb = new StringBuilder();
        Set<Modifier> implicit = switch (t.getKind()) {
            case INTERFACE, ANNOTATION_TYPE -> Set.of(Modifier.ABSTRACT, Modifier.STATIC);
            case ENUM, RECORD -> Set.of(Modifier.FINAL, Modifier.STATIC);
            default -> Set.of();
        };
        sb.append(modifiers(t, implicit));
        sb.append(switch (t.getKind()) {
            case INTERFACE -> "interface ";
            case ANNOTATION_TYPE -> "@interface ";
            case ENUM -> "enum ";
            case RECORD -> "record ";
            default -> "class ";
        });
        sb.append(t.getSimpleName()).append(typeParameters(t.getTypeParameters()));
        if (t.getKind() == ElementKind.RECORD) {
            List<String> components = new ArrayList<>();
            for (RecordComponentElement c : t.getRecordComponents()) {
                components.add(type(c.asType()) + " " + c.getSimpleName());
            }
            sb.append('(').append(String.join(", ", components)).append(')');
        }
        TypeMirror superclass = t.getSuperclass();
        if (t.getKind() == ElementKind.CLASS && superclass.getKind() == TypeKind.DECLARED
                && !((TypeElement) types.asElement(superclass)).getQualifiedName().contentEquals("java.lang.Object")) {
            sb.append(" extends ").append(type(superclass));
        }
        List<String> interfaces = t.getInterfaces().stream().map(this::type).toList();
        if (!interfaces.isEmpty() && t.getKind() != ElementKind.ANNOTATION_TYPE) {
            sb.append(t.getKind() == ElementKind.INTERFACE ? " extends " : " implements ")
                    .append(String.join(", ", interfaces));
        }
        return sb.toString();
    }

    /** {@code public static <T> Value<T> of(T initial) throws IOException}. */
    String executable(ExecutableElement e, boolean inInterface) {
        StringBuilder sb = new StringBuilder(modifiers(e,
                inInterface ? Set.of(Modifier.PUBLIC, Modifier.ABSTRACT) : Set.of()));
        String typeParameters = typeParameters(e.getTypeParameters());
        if (!typeParameters.isEmpty()) {
            sb.append(typeParameters).append(' ');
        }
        if (e.getKind() == ElementKind.CONSTRUCTOR) {
            sb.append(e.getEnclosingElement().getSimpleName());
        } else {
            sb.append(type(e.getReturnType())).append(' ').append(e.getSimpleName());
        }
        List<String> params = new ArrayList<>();
        List<? extends VariableElement> ps = e.getParameters();
        for (int i = 0; i < ps.size(); i++) {
            VariableElement p = ps.get(i);
            String type = type(p.asType());
            if (e.isVarArgs() && i == ps.size() - 1) {
                type = type.substring(0, type.length() - 2) + "...";
            }
            params.add(type + " " + p.getSimpleName());
        }
        sb.append('(').append(String.join(", ", params)).append(')');
        if (e.getDefaultValue() != null) {
            sb.append(" default ").append(e.getDefaultValue());
        }
        if (!e.getThrownTypes().isEmpty()) {
            sb.append(" throws ").append(e.getThrownTypes().stream().map(this::type).collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }

    /** {@code public static final int WAIT = 20}. */
    String field(VariableElement v, boolean inInterface) {
        String sb = modifiers(v, inInterface ? Set.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL) : Set.of())
                + type(v.asType()) + " " + v.getSimpleName();
        Object constant = v.getConstantValue();
        if (constant == null) {
            return sb;
        }
        return sb + " = " + (constant instanceof String s ? '"' + s + '"' : constant instanceof Character c ? "'" + c + "'" : constant);
    }

    /** {@code name(Type, Type)}, the parameter types erased: how a heading and an anchor tell overloads apart. */
    String erasedName(ExecutableElement e) {
        String name = e.getKind() == ElementKind.CONSTRUCTOR ? e.getEnclosingElement().getSimpleName().toString()
                : e.getSimpleName().toString();
        return name + "(" + e.getParameters().stream().map(p -> type(types.erasure(p.asType())))
                .collect(Collectors.joining(", ")) + ")";
    }

    private static String modifiers(Element e, Set<Modifier> implicit) {
        StringBuilder sb = new StringBuilder();
        for (Modifier m : ORDER) {
            if (e.getModifiers().contains(m) && !implicit.contains(m)) {
                sb.append(m).append(' ');
            }
        }
        return sb.toString();
    }

    /** {@code <T extends Foo, U>}, or {@code ""}. */
    private String typeParameters(List<? extends TypeParameterElement> ps) {
        if (ps.isEmpty()) {
            return "";
        }
        List<String> out = new ArrayList<>();
        for (TypeParameterElement p : ps) {
            List<String> bounds = p.getBounds().stream()
                    .filter(b -> !(b.getKind() == TypeKind.DECLARED
                            && ((TypeElement) types.asElement(b)).getQualifiedName().contentEquals("java.lang.Object")))
                    .map(this::type).toList();
            out.add(p.getSimpleName() + (bounds.isEmpty() ? "" : " extends " + String.join(" & ", bounds)));
        }
        return "<" + String.join(", ", out) + ">";
    }
}
