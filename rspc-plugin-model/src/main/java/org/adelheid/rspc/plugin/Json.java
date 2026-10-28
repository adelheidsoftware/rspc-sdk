package org.adelheid.rspc.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The little JSON {@code plugin.json} needs: its few pieces written the way the official example lays them out, and a
 * hand-written file read into plain Java values, so that the build can check what it says.
 */
final class Json {

	/** What {@link #parse} gives for JSON's {@code null}, since the maps and lists it builds hold no Java null. */
	static final Object NULL = new Object() {
		@Override
		public String toString() {
			return "null";
		}
	};

	private Json() {
	}

	/** A JSON string, with the characters JSON requires escaped. */
	static String string(String text) {
		StringBuilder escaped = new StringBuilder("\"");
		for (int index = 0; index < text.length(); index++) {
			char c = text.charAt(index);
			switch (c) {
				case '"' -> escaped.append("\\\"");
				case '\\' -> escaped.append("\\\\");
				case '\n' -> escaped.append("\\n");
				case '\r' -> escaped.append("\\r");
				case '\t' -> escaped.append("\\t");
				default -> {
					if (c < 0x20) {
						escaped.append(String.format("\\u%04x", (int) c));
					} else {
						escaped.append(c);
					}
				}
			}
		}
		return escaped.append('"').toString();
	}

	/** A list of strings on one line: {@code []}, or {@code [ "a", "b" ]}. */
	static String list(List<String> values) {
		return values.isEmpty()
				? "[]"
				: "[ " + values.stream().map(Json::string).collect(Collectors.joining(", ")) + " ]";
	}

	/** An object of values already written as JSON, on one line: {@code {}}, or {@code { "a": 1 }}. */
	static String object(Map<String, String> values) {
		return values.isEmpty()
				? "{}"
				: "{ " + values.entrySet().stream()
						.map(entry -> string(entry.getKey()) + ": " + entry.getValue())
						.collect(Collectors.joining(", ")) + " }";
	}

	/**
	 * The value a JSON text holds: an object as a {@link Map} in the order of its fields, an array as a {@link List},
	 * a string, a number as a {@link Long} where it is whole and a {@link Double} otherwise, a {@link Boolean}, or
	 * {@link #NULL}.
	 *
	 * @throws IllegalArgumentException for text that is not JSON, saying where it goes wrong
	 */
	static Object parse(String text) {
		Reader reader = new Reader(text);
		Object value = reader.value();
		reader.skipSpace();
		if (!reader.atEnd()) {
			throw reader.error("there is text after the value");
		}
		return value;
	}

	/** Reads one JSON text from its start, keeping its place for the messages. */
	private static final class Reader {

		private final String text;
		private int at;

		Reader(String text) {
			this.text = text;
		}

		boolean atEnd() {
			return at >= text.length();
		}

		IllegalArgumentException error(String what) {
			int line = 1;
			for (int index = 0; index < Math.min(at, text.length()); index++) {
				if (text.charAt(index) == '\n') {
					line++;
				}
			}
			return new IllegalArgumentException(what + " at line " + line);
		}

		void skipSpace() {
			while (!atEnd() && Character.isWhitespace(text.charAt(at))) {
				at++;
			}
		}

		char peek() {
			if (atEnd()) {
				throw error("the text ends before the value is complete");
			}
			return text.charAt(at);
		}

		void expect(char c) {
			if (peek() != c) {
				throw error("'" + c + "' is expected but '" + peek() + "' is there");
			}
			at++;
		}

		Object value() {
			skipSpace();
			char c = peek();
			if (c == '{') {
				return object();
			}
			if (c == '[') {
				return array();
			}
			if (c == '"') {
				return string();
			}
			if (c == '-' || Character.isDigit(c)) {
				return number();
			}
			if (text.startsWith("true", at)) {
				at += 4;
				return true;
			}
			if (text.startsWith("false", at)) {
				at += 5;
				return false;
			}
			if (text.startsWith("null", at)) {
				at += 4;
				return NULL;
			}
			throw error("a value is expected but '" + c + "' is there");
		}

		Map<String, Object> object() {
			expect('{');
			Map<String, Object> fields = new LinkedHashMap<>();
			skipSpace();
			if (peek() == '}') {
				at++;
				return fields;
			}
			while (true) {
				skipSpace();
				if (peek() != '"') {
					throw error("a field's name, in double quotes, is expected");
				}
				String name = string();
				skipSpace();
				expect(':');
				if (fields.put(name, value()) != null) {
					throw error("the field " + name + " appears twice");
				}
				skipSpace();
				if (peek() == ',') {
					at++;
					continue;
				}
				expect('}');
				return fields;
			}
		}

		List<Object> array() {
			expect('[');
			List<Object> values = new ArrayList<>();
			skipSpace();
			if (peek() == ']') {
				at++;
				return values;
			}
			while (true) {
				values.add(value());
				skipSpace();
				if (peek() == ',') {
					at++;
					continue;
				}
				expect(']');
				return values;
			}
		}

		String string() {
			expect('"');
			StringBuilder value = new StringBuilder();
			while (true) {
				char c = peek();
				at++;
				if (c == '"') {
					return value.toString();
				}
				if (c != '\\') {
					value.append(c);
					continue;
				}
				char escaped = peek();
				at++;
				switch (escaped) {
					case '"', '\\', '/' -> value.append(escaped);
					case 'n' -> value.append('\n');
					case 'r' -> value.append('\r');
					case 't' -> value.append('\t');
					case 'b' -> value.append('\b');
					case 'f' -> value.append('\f');
					case 'u' -> {
						if (at + 4 > text.length()) {
							throw error("a \\u escape needs four hex digits");
						}
						try {
							value.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
						} catch (NumberFormatException e) {
							throw error("a \\u escape needs four hex digits");
						}
						at += 4;
					}
					default -> throw error("'\\" + escaped + "' is not an escape JSON knows");
				}
			}
		}

		Object number() {
			int start = at;
			if (peek() == '-') {
				at++;
			}
			boolean whole = true;
			while (!atEnd()) {
				char c = text.charAt(at);
				if (Character.isDigit(c)) {
					at++;
				} else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
					whole = false;
					at++;
				} else {
					break;
				}
			}
			String digits = text.substring(start, at);
			try {
				return whole ? (Object) Long.parseLong(digits) : (Object) Double.parseDouble(digits);
			} catch (NumberFormatException e) {
				throw error(digits + " is not a number");
			}
		}
	}
}
