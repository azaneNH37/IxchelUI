// The doclet that writes the docs site's Java API pages: Markdown for the site's theme, one page per
// @HostApi type. Run by each library module's `javaApi` task; not published.
plugins {
    id("ixchel.java-conventions")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}
