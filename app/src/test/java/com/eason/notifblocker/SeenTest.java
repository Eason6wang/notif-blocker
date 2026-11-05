package com.eason.notifblocker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SeenTest {
    private static Seen seen(boolean clearable, String result) {
        return new Seen(1L, "pkg", "App", "Title", "Text", "", false, clearable, result);
    }

    @Test
    public void filtersMatchBlockedAndNotSwipeable() {
        Seen plain = seen(true, "");
        Seen blocked = seen(true, "dismissed");
        Seen stuck = seen(false, "still showing");
        assertTrue(Seen.Filter.ALL.accepts(plain));
        assertFalse(Seen.Filter.BLOCKED.accepts(plain));
        assertTrue(Seen.Filter.BLOCKED.accepts(blocked));
        assertTrue(Seen.Filter.BLOCKED.accepts(stuck));
        assertFalse(Seen.Filter.NOT_SWIPEABLE.accepts(blocked));
        assertTrue(Seen.Filter.NOT_SWIPEABLE.accepts(stuck));
        assertEquals("Not swipeable", Seen.Filter.NOT_SWIPEABLE.label);
    }
}
