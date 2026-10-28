package org.adelheid.rspc.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The JSON reader, which turns a hand-written plugin.json into plain values. */
class JsonTest {

	@Test
	void readsEveryKindOfValue() {
		Object value = Json.parse("""
				{
				    "name": "Herb \\"Tracker\\"\\n",
				    "deps": { "prettyui": 6398181, "local": "rsprite" },
				    "flags": [ true, false, null ],
				    "ratio": -1.5e2,
				    "unicode": "\\u0041"
				}
				""");
		Map<String, Object> expected = new java.util.LinkedHashMap<>();
		expected.put("name", "Herb \"Tracker\"\n");
		expected.put("deps", Map.of("prettyui", 6398181L, "local", "rsprite"));
		expected.put("flags", List.of(true, false, Json.NULL));
		expected.put("ratio", -150.0);
		expected.put("unicode", "A");
		assertEquals(expected, value);
		assertEquals(List.of("deps", "flags", "name", "ratio", "unicode").size(), ((Map<?, ?>) value).size());
		assertSame(Json.NULL, Json.parse("null"));
	}

	@Test
	void saysWhereTextStopsBeingJson() {
		assertEquals("there is text after the value at line 1",
				assertThrows(IllegalArgumentException.class, () -> Json.parse("{} {}")).getMessage());
		assertEquals("the field a appears twice at line 3",
				assertThrows(IllegalArgumentException.class, () -> Json.parse("{\n\"a\": 1,\n\"a\": 2 }"))
						.getMessage());
		assertEquals("'\\x' is not an escape JSON knows at line 1",
				assertThrows(IllegalArgumentException.class, () -> Json.parse("\"\\x\"")).getMessage());
		assertEquals("the text ends before the value is complete at line 1",
				assertThrows(IllegalArgumentException.class, () -> Json.parse("[1, 2")).getMessage());
		assertEquals("a value is expected but '}' is there at line 1",
				assertThrows(IllegalArgumentException.class, () -> Json.parse("{ \"a\": }")).getMessage());
	}
}
