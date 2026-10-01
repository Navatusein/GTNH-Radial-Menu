package com.navatusein.radialmenu.core.json;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Reads an action's parameters, where a value may be written as an array of lines.
 *
 * <p>
 * Multi-line parameters are the reason: a chain of commands is readable enough as {@code "a\nb"}, a thirty-line Lua
 * script is not. Both forms mean the same string, and a value with newlines in it is written back out as an array, so a
 * file edited by hand and then saved from the editor comes back looking the way it was written.
 *
 * <p>
 * Single-line values stay plain strings, which is what every profile written before this already holds.
 */
public class StringMapAdapterFactory implements TypeAdapterFactory {

    @SuppressWarnings("unchecked")
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!Map.class.isAssignableFrom(type.getRawType()) || !(type.getType() instanceof ParameterizedType)) {
            return null;
        }
        Type[] arguments = ((ParameterizedType) type.getType()).getActualTypeArguments();
        if (arguments.length != 2 || arguments[0] != String.class || arguments[1] != String.class) {
            return null;
        }
        return (TypeAdapter<T>) new Adapter();
    }

    private static final class Adapter extends TypeAdapter<Map<String, String>> {

        @Override
        public void write(JsonWriter out, Map<String, String> value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            for (Map.Entry<String, String> entry : value.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                out.name(entry.getKey());
                if (entry.getValue()
                    .indexOf('\n') < 0) {
                    out.value(entry.getValue());
                } else {
                    out.beginArray();
                    for (String line : entry.getValue()
                        .split("\n", -1)) {
                        out.value(line);
                    }
                    out.endArray();
                }
            }
            out.endObject();
        }

        @Override
        public Map<String, String> read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            Map<String, String> values = new LinkedHashMap<>();
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                String value = readValue(in);
                if (value != null) {
                    values.put(name, value);
                }
            }
            in.endObject();
            return values;
        }

        /**
         * One value, however it was written.
         *
         * <p>
         * A null is dropped rather than stored: a parameter present but empty would read as a deliberate blank, and
         * every reader of these values already treats a missing one as "use the default".
         */
        private String readValue(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            if (in.peek() == JsonToken.BOOLEAN) {
                // Gson's own string reader coerces a number but not a boolean, and "cycle": true in a hand-written
                // file threw hard enough to take the whole profile down with it. Every value here is a string to the
                // rest of the mod, so reading one as its text is the repair this codec is supposed to make.
                return Boolean.toString(in.nextBoolean());
            }
            if (in.peek() != JsonToken.BEGIN_ARRAY) {
                return in.nextString();
            }

            StringBuilder joined = new StringBuilder();
            in.beginArray();
            boolean first = true;
            while (in.hasNext()) {
                if (!first) {
                    joined.append('\n');
                }
                first = false;
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                } else if (in.peek() == JsonToken.BOOLEAN) {
                    joined.append(in.nextBoolean());
                } else {
                    joined.append(in.nextString());
                }
            }
            in.endArray();
            return joined.toString();
        }
    }
}
