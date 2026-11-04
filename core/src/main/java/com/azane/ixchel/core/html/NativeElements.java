package com.azane.ixchel.core.html;

import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.event.InteractionFacet;
import com.azane.ixchel.core.layout.LayoutFacet;
import com.azane.ixchel.core.paint.PaintFacet;
import com.azane.ixchel.core.style.StyleFacet;
import com.azane.ixchel.core.template.ContentModel;
import com.azane.ixchel.core.template.NativeElement;
import com.azane.ixchel.core.template.NativePayload;
import com.azane.ixchel.core.template.TagRegistry;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Annotation adapter shared by runtime and kit. Does not instantiate or initialize the declaring class. */
public final class NativeElements {
    private NativeElements() {}

    private static final LayoutFacet LAYOUT = new LayoutFacet() {
        @Override public boolean atomic() { return true; }
    };
    /**
     * Hands the element to the host's painter while it has a payload: bound for an element that
     * declares one, set by the host itself ({@code Element.setPayload}) on one that does not.
     */
    private static final PaintFacet PAINT = new PaintFacet() {
        @Override public void paint(Element e, float y, DisplayList out, Context context) {
            if (e.payload() == null) return;
            LayoutBox b = e.box();
            out.add(new DrawCommand.Custom(e.tag(), e.payload(), b.x(), y, b.width(), b.height()));
        }
    };

    public static ElementType declare(Class<?> declaring) {
        NativeElement d = declaring.getAnnotation(NativeElement.class);
        if (d == null) throw new IllegalArgumentException(declaring.getName() + " has no @NativeElement");
        if (InteractionFacet.class.isAssignableFrom(declaring) || LayoutFacet.class.isAssignableFrom(declaring)) {
            throw new IllegalArgumentException("interaction and layout are internal element contracts, not host extensions");
        }
        Type payloadType = payloadTypeOf(declaring, Map.of());
        String where = declaring.getName() + " declaring <" + d.tag() + ">: ";
        ElementType type;
        if (d.payload().isEmpty()) {
            if (payloadType != null) throw new IllegalArgumentException(where + "implements NativePayload but declares no payload");
            type = ElementType.builder(d.tag(), d.attributes()).facet(PaintFacet.KEY, PAINT).build();
        } else {
            if (payloadType == null) throw new IllegalArgumentException(where + "declares a payload but does not implement NativePayload");
            type = type(d.tag(), Set.of(d.attributes()), d.payload(), rawClass(payloadType));
        }
        // Both entry points enforce the same metadata rules, even before a document is constructed.
        TagRegistry.builder().add(type);
        return type;
    }

    public static ElementType type(String tag, Set<String> attributes, String payload, Class<?> payloadType) {
        return ElementType.builder(tag, attributes.toArray(String[]::new)).payload(payload, payloadType)
                .facet(ContentModel.KEY, HtmlContent.NATIVE)
                .facet(StyleFacet.KEY, new StyleFacet("display: inline"))
                .facet(LayoutFacet.KEY, LAYOUT).facet(PaintFacet.KEY, PAINT)
                .build();
    }

    private static Type payloadTypeOf(Type type, Map<TypeVariable<?>, Type> vars) {
        Class<?> raw;
        Map<TypeVariable<?>, Type> bound = new HashMap<>();
        if (type instanceof ParameterizedType p) {
            raw = (Class<?>) p.getRawType();
            TypeVariable<?>[] params = raw.getTypeParameters();
            Type[] args = p.getActualTypeArguments();
            for (int i = 0; i < params.length; i++) {
                Type a = args[i];
                bound.put(params[i], a instanceof TypeVariable<?> v && vars.containsKey(v) ? vars.get(v) : a);
            }
        } else if (type instanceof Class<?> c) raw = c;
        else return null;
        if (raw == NativePayload.class) return bound.getOrDefault(raw.getTypeParameters()[0], Object.class);
        List<Type> supers = new ArrayList<>(List.of(raw.getGenericInterfaces()));
        if (raw.getGenericSuperclass() != null) supers.add(raw.getGenericSuperclass());
        for (Type s : supers) {
            Type found = payloadTypeOf(s, bound);
            if (found != null) return found;
        }
        return null;
    }
    private static Class<?> rawClass(Type t) {
        if (t instanceof Class<?> c) return c;
        if (t instanceof ParameterizedType p) return (Class<?>) p.getRawType();
        return Object.class;
    }
}
