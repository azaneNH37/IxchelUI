package com.azane.ixchel.kit.lint;

import com.azane.ixchel.core.binding.compile.BindingCode;
import com.azane.ixchel.core.diag.DiagCode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeCatalogTest {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*/[a-z0-9-]+");

    @Test
    void everyCodeIdIsWellFormedAndUnique() {
        List<DiagCode> codes = CodeCatalog.all(CodeCatalogTest.class.getClassLoader());
        Set<String> ids = new HashSet<>();
        for (DiagCode c : codes) {
            assertTrue(ID.matcher(c.id()).matches(), "malformed id " + c.id() + " of " + c.getClass().getName());
            assertTrue(ids.add(c.id()), "duplicate id " + c.id());
        }
        assertTrue(codes.contains(BindingCode.UNKNOWN_NAME), "core codes found");
        assertTrue(codes.containsAll(List.of(KitCode.values())), "kit codes found");
    }

    @Test
    void findsACodeById() {
        assertEquals(KitCode.MISSING_VIEW, CodeCatalog.find(CodeCatalogTest.class.getClassLoader(), "model/missing-view"));
    }
}
