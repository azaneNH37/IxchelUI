package com.azane.ixchel.mc.container;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Problems placing a container screen's menu slots with {@code <mc-slot>} elements. */
public enum ContainerCode implements DiagCode {
    MISSING_INDEX("container/missing-index", Severity.ERROR,
            "An <mc-slot> has no slot index; give it index=\"N\" or bind:index."),
    MALFORMED_INDEX("container/malformed-index", Severity.ERROR,
            "An <mc-slot>'s index is not an integer."),
    INDEX_OUT_OF_RANGE("container/index-out-of-range", Severity.ERROR,
            "An <mc-slot>'s index is negative or not below the menu's slot count."),
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
