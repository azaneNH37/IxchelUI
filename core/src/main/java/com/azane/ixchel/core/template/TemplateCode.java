package com.azane.ixchel.core.template;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Problems of template markup and of loading the files a template links. */
public enum TemplateCode implements DiagCode {
    SYNTAX("template/syntax", Severity.ERROR,
            "The markup is malformed: an unterminated construct, an unexpected character or a missing name."),
    UNSUPPORTED_MARKUP("template/unsupported-markup", Severity.ERROR,
            "Doctypes, CDATA sections, processing instructions and stray '<' characters are not supported."),
    INVALID_CHARACTER_REFERENCE("template/invalid-character-reference", Severity.ERROR,
            "An '&' does not start a valid character reference."),
    UNQUOTED_ATTRIBUTE("template/unquoted-attribute", Severity.ERROR,
            "An attribute has no value or its value is not quoted."),
    DUPLICATE_ATTRIBUTE("template/duplicate-attribute", Severity.ERROR,
            "The same attribute appears twice on one element."),
    UNCLOSED_TAG("template/unclosed-tag", Severity.ERROR,
            "An element is opened but never closed."),
    MISMATCHED_CLOSING_TAG("template/mismatched-closing-tag", Severity.ERROR,
            "A closing tag does not match the element that is open."),
    UNKNOWN_TAG("template/unknown-tag", Severity.ERROR,
            "The tag is neither a built-in element, a registered native element nor a linked component."),
    MISPLACED_ELEMENT("template/misplaced-element", Severity.ERROR,
            "An element appears where it is not allowed, such as <style> or <link> below the top level."),
    SLOT_IN_FOR("template/slot-in-for", Severity.ERROR,
            "A <slot> is inside a <for>, but each slot is filled once."),
    MISPLACED_SLOT_ATTRIBUTE("template/misplaced-slot-attribute", Severity.ERROR,
            "A slot=\"…\" attribute is not on a direct child of a component's element."),
    INVALID_CHILDREN("template/invalid-children", Severity.ERROR,
            "An element has children it does not accept."),
    IMG_WITHOUT_SOURCE("template/img-without-source", Severity.ERROR,
            "An <img> has neither a 'src' attribute nor a 'bind:src' binding."),
    UNSUPPORTED_INPUT_TYPE("template/unsupported-input-type", Severity.ERROR,
            "An <input> has a type other than text, number, checkbox or range."),
    INVALID_LINK("template/invalid-link", Severity.ERROR,
            "A <link> is not self-closing or lacks a valid rel or an href."),
    LINK_AFTER_ELEMENT("template/link-after-element", Severity.ERROR,
            "A <link rel=\"component\"> comes after the first element."),
    UNKNOWN_ATTRIBUTE("template/unknown-attribute", Severity.ERROR,
            "The element does not accept this attribute."),
    MOVED_ATTRIBUTE("template/moved-attribute", Severity.ERROR,
            "'if' and 'for' are blocks now, not attributes; wrap the element in the block."),
    UNSUPPORTED_ATTRIBUTE("template/unsupported-attribute", Severity.ERROR,
            "The attribute name is reserved but not supported yet."),
    UNKNOWN_BLOCK_ATTRIBUTE("template/unknown-block-attribute", Severity.ERROR,
            "A block or component declaration element has an attribute it does not take."),
    MISSING_BLOCK_ATTRIBUTE("template/missing-block-attribute", Severity.ERROR,
            "A block or component declaration element lacks a required attribute."),
    COMPONENT_FILE_CONTENT("template/component-file-content", Severity.ERROR,
            "A component file holds something other than <link> declarations and one <template>."),
    MISSING_COMPONENT_TEMPLATE("template/missing-component-template", Severity.ERROR,
            "A component file has no <template name=\"…\">."),
    INVALID_COMPONENT_NAME("template/invalid-component-name", Severity.ERROR,
            "A component name lacks a '-', starts with 'ix-' or 'mc-', or is already a registered tag."),
    INVALID_MEMBER_NAME("template/invalid-member-name", Severity.ERROR,
            "A component parameter or event name is not a letter followed by letters or digits."),
    RESERVED_MEMBER_NAME("template/reserved-member-name", Severity.ERROR,
            "A component parameter or event name is taken by the component's element."),
    DUPLICATE_MEMBER("template/duplicate-member", Severity.ERROR,
            "A component declares the same parameter or event name twice."),
    DOM_EVENT_NAME("template/dom-event-name", Severity.ERROR,
            "A component event has the name of a DOM event."),
    DUPLICATE_SLOT("template/duplicate-slot", Severity.ERROR,
            "A component template has two slots with the same name."),
    UNREADABLE_FILE("load/unreadable-file", Severity.ERROR,
            "A linked file's href is invalid or the file cannot be read."),
    FILE_NOT_FOUND("load/file-not-found", Severity.ERROR,
            "A linked file does not exist."),
    COMPONENT_CYCLE("load/component-cycle", Severity.ERROR,
            "A component file links back to itself through other component files."),
    DUPLICATE_COMPONENT("load/duplicate-component", Severity.ERROR,
            "Two linked component files declare the same component name."),
    COMPONENT_LINK_UNAVAILABLE("load/component-link-unavailable", Severity.ERROR,
            "Components are linked from a template that was not loaded from files."),
    LIBRARY_SHEET_NOT_ON_PAGE("load/library-sheet-not-on-page", Severity.ERROR,
            "A component file links a library stylesheet, which only the page may link."),
    UNKNOWN_LIBRARY_SHEET("load/unknown-library-sheet", Severity.ERROR,
            "The linked library stylesheet name does not exist.");

    private final String id;
    private final Severity severity;
    private final String summary;

    TemplateCode(String id, Severity severity, String summary) {
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
