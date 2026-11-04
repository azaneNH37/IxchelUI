package com.azane.ixchel.core.view;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Problems of view markup. */
public enum ViewCode implements DiagCode {
    SYNTAX("view/syntax", Severity.ERROR,
            "The markup is malformed: an unterminated construct, an unexpected character or a missing name."),
    UNSUPPORTED_MARKUP("view/unsupported-markup", Severity.ERROR,
            "Doctypes, CDATA sections, processing instructions and stray '<' characters are not supported."),
    INVALID_CHARACTER_REFERENCE("view/invalid-character-reference", Severity.ERROR,
            "An '&' does not start a valid character reference."),
    UNQUOTED_ATTRIBUTE("view/unquoted-attribute", Severity.ERROR,
            "An attribute has no value or its value is not quoted."),
    DUPLICATE_ATTRIBUTE("view/duplicate-attribute", Severity.ERROR,
            "The same attribute appears twice on one element."),
    UNCLOSED_TAG("view/unclosed-tag", Severity.ERROR,
            "An element is opened but never closed."),
    MISMATCHED_CLOSING_TAG("view/mismatched-closing-tag", Severity.ERROR,
            "A closing tag does not match the element that is open."),
    UNKNOWN_TAG("view/unknown-tag", Severity.ERROR,
            "The tag is neither an element nor a component the view links."),
    MISPLACED_ELEMENT("view/misplaced-element", Severity.ERROR,
            "An element appears where it is not allowed, such as <link> below the root or <component> inside a view."),
    SLOT_IN_FOR("view/slot-in-for", Severity.ERROR,
            "A <slot> is inside a <for>, but each slot is filled once."),
    MISPLACED_SLOT_ATTRIBUTE("view/misplaced-slot-attribute", Severity.ERROR,
            "A slot=\"…\" attribute is not on a direct child of a component's element."),
    INVALID_CHILDREN("view/invalid-children", Severity.ERROR,
            "An element has children it does not accept."),
    IMG_WITHOUT_SOURCE("view/img-without-source", Severity.ERROR,
            "An <img> has neither a 'src' attribute nor a 'bind:src' binding."),
    UNSUPPORTED_INPUT_TYPE("view/unsupported-input-type", Severity.ERROR,
            "An <input> has a type other than text, number, checkbox or range."),
    INVALID_LINK("view/invalid-link", Severity.ERROR,
            "A <link> is not self-closing, lacks an href, or is a second stylesheet of the view."),
    LINK_AFTER_ELEMENT("view/link-after-element", Severity.ERROR,
            "A <link> comes after the root's first element."),
    LINK_UNAVAILABLE("view/link-unavailable", Severity.ERROR,
            "A view that was not loaded from files links something."),
    MISSING_ROOT("view/missing-root", Severity.ERROR,
            "A view file is not exactly one <page> or <component> root element with only comments and whitespace around it."),
    UNKNOWN_ATTRIBUTE("view/unknown-attribute", Severity.ERROR,
            "The element does not accept this attribute."),
    MOVED_ATTRIBUTE("view/moved-attribute", Severity.ERROR,
            "'if' and 'for' are blocks now, not attributes; wrap the element in the block."),
    INVALID_MESSAGE_ELEMENT("i18n/invalid-message-element", Severity.ERROR,
            "A t=\"…\" is on an element whose content is not its own (a component's element, an <input>, <img>, <select> or drawn element) or that also binds its text."),
    INVALID_MESSAGE_CHILD("i18n/invalid-message-child", Severity.ERROR,
            "A child of a t=\"…\" element is text, a <for>, <if> or <slot>, a mark element with content, or a second mark of the same name."),
    MISPLACED_MARK("i18n/misplaced-mark", Severity.ERROR,
            "A mark=\"…\" is not on a direct child of a t=\"…\" element."),
    INVALID_TRANSLATED_ATTRIBUTE("i18n/invalid-translated-attribute", Severity.ERROR,
            "A t:<attribute> names an attribute the element does not take, one that cannot be translated, or one also given directly."),
    MISSING_TRANSLATION("i18n/missing-translation", Severity.ERROR,
            "No language file has the key."),
    INVALID_TRANSLATION("i18n/invalid-translation", Severity.ERROR,
            "A translation in the base language has an unclosed or nested mark, an unsupported '%' conversion or an argument number beyond the arguments, or leaves a mark or argument unused."),
    LOCALE_TRANSLATION("i18n/locale-translation", Severity.WARNING,
            "A translation outside the base language has an unclosed or nested mark, an unsupported '%' conversion or an argument number beyond the arguments; it is shown as far as it can be."),
    UNDECLARED_MARK("i18n/undeclared-mark", Severity.WARNING,
            "A translation holds what looks like a mark tag whose name the element does not declare; it is shown as text."),
    LITERAL_TEXT("i18n/literal-text", Severity.WARNING,
            "Text or a title or placeholder is written in the view instead of taken from the language files; opt in, and mark untranslatable content translate=\"no\".") {
        @Override
        public boolean optIn() {
            return true;
        }
    },
    UNKNOWN_BLOCK_ATTRIBUTE("view/unknown-block-attribute", Severity.ERROR,
            "A block or component declaration element has an attribute it does not take."),
    MISSING_BLOCK_ATTRIBUTE("view/missing-block-attribute", Severity.ERROR,
            "A block or component declaration element lacks a required attribute."),
    INVALID_COMPONENT_NAME("view/invalid-component-name", Severity.ERROR,
            "A component name lacks a '-', starts with 'ix-' or 'mc-' outside the library, or is already a registered tag."),
    INVALID_MEMBER_NAME("view/invalid-member-name", Severity.ERROR,
            "A component parameter or event name is not a letter followed by letters or digits."),
    RESERVED_MEMBER_NAME("view/reserved-member-name", Severity.ERROR,
            "A component parameter or event name is taken by the component's element."),
    DUPLICATE_MEMBER("view/duplicate-member", Severity.ERROR,
            "A component declares the same parameter or event name twice."),
    DOM_EVENT_NAME("view/dom-event-name", Severity.ERROR,
            "A component event has the name of a DOM event."),
    DUPLICATE_SLOT("view/duplicate-slot", Severity.ERROR,
            "A component view has two slots with the same name.");

    private final String id;
    private final Severity severity;
    private final String summary;

    ViewCode(String id, Severity severity, String summary) {
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
