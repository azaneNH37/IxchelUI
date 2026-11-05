package com.azane.ixchel.mc.container;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Problems placing a container screen's menu slots with {@code <mc-slot>} elements. */
public enum ContainerCode implements DiagCode {
    MISSING_SLOT("container/missing-slot", Severity.ERROR,
            "An <mc-slot> in a container screen has no slot; bind:menuslot gives it one of the menu's."),
    FOREIGN_SLOT("container/foreign-slot", Severity.ERROR,
            "An <mc-slot> is bound to a slot that is not one of the container screen's menu."),
    DUPLICATE_SLOT("container/duplicate-slot", Severity.ERROR,
            "Two <mc-slot> elements place the same menu slot; the later one is ignored."),
    SLOT_TOO_SMALL("container/slot-too-small", Severity.ERROR,
            "A displayed <mc-slot>'s border box is smaller than the 16x16 item area."),
    UNPLACED_SLOT("container/unplaced-slot", Severity.ERROR,
            "A menu slot has no <mc-slot> element; every slot of the menu must be placed.");

    private final String id;
    private final Severity severity;
    private final String summary;

    ContainerCode(String id, Severity severity, String summary) {
        this.id = id;
        this.severity = severity;
        this.summary = summary;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Severity severity() {
        return severity;
    }

    @Override
    public String summary() {
        return summary;
    }
}
