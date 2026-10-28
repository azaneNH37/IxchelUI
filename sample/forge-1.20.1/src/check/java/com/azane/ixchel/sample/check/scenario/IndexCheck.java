package com.azane.ixchel.sample.check.scenario;

import com.azane.ixchel.sample.check.Checks;
import com.azane.ixchel.sample.check.Script;
import com.azane.ixchel.sample.client.index.SampleIndex;
import com.azane.ixchel.sample.client.index.Samples;

/** {@code -Pcheck=index}: opens the sample index (H) and expects a row per sample. */
public final class IndexCheck {
    private IndexCheck() {
    }

    public static void register() {
        Checks.register("index", s -> s
                .run(SampleIndex::open)
                .waitUntil("index lists every sample", () -> Script.isOpen()
                        && Script.query(".row").size() == Samples.all().size(), Script.WAIT)
                .shot("sample-index"));
    }
}
