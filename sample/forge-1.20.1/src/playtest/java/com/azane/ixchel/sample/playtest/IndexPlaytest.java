package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.sample.client.index.SampleIndex;
import com.azane.ixchel.sample.client.index.Samples;

/** {@code -Pplaytest=index}: opens the sample index (H) and expects a row per sample. */
public final class IndexPlaytest {
    private IndexPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("index", s -> s
                .run(SampleIndex::open)
                .waitUntil("index lists every sample", () -> Playtest.isOpen()
                        && Playtest.query(".row").size() == Samples.all().size(), Playtest.WAIT)
                .shot("sample-index"));
    }
}
