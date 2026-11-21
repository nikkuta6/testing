package by.pranovich.ide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdeServerTest {

    private static final String PAINTER = "int n = 0; fun void onClick(int x, int y) { n = n + 1; point(x, y); print(n); }";

    @Test
    void buildsHandlerCallsFromClicks() {
        assertEquals("", IdeServer.clickCalls(null));
        assertEquals("\nonClick(10, 20);\nonClick(-1, 0);", IdeServer.clickCalls("clicks=10,20;-1,0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x=1,2", "clicks=1", "clicks=1,2,3", "clicks=a,2", "clicks=1,2);print(9"})
    void rejectsMalformedClicks(String query) {
        assertThrows(IllegalArgumentException.class, () -> IdeServer.clickCalls(query));
    }

    @Test
    void replaysClicksInOrderKeepingGlobals() {
        String json = IdeServer.run(PAINTER, false, IdeServer.clickCalls("clicks=1,2;3,4"));
        assertTrue(json.contains("\"onClick\":true"), json);
        assertTrue(json.contains("\"canvas\":[\"point 1.0 2.0\",\"point 3.0 4.0\"]"), json);
        assertTrue(json.contains("\"output\":\"1\\n2\\n\""), json);
    }

    @Test
    void ignoresClicksWithoutHandler() {
        String json = IdeServer.run("point(5, 5);", false, IdeServer.clickCalls("clicks=1,2"));
        assertTrue(json.contains("\"onClick\":false"), json);
        assertTrue(json.contains("\"canvas\":[\"point 5.0 5.0\"]"), json);
    }
}
