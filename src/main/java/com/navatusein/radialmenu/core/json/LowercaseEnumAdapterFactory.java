package com.navatusein.radialmenu.core.json;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Writes enum constants in lower case and reads them back case-insensitively.
 *
 * <p>
 * Done with an adapter rather than {@code @SerializedName} because Minecraft 1.7.10 ships Gson 2.2.4, which ignores
 * that annotation on enum constants. Reading case-insensitively also means a config file edited by hand with
 * {@code "FIXED"} still loads.
 *
 * <p>
 * An unrecognised value deserialises to null rather than throwing, so one bad field cannot take the whole menu down;
 * the model's {@code normalize()} methods then substitute a default.
 */
public class LowercaseEnumAdapterFactory implements TypeAdapterFactory {

    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        Class<? super T> raw = type.getRawType();
        if (!Enum.class.isAssignableFrom(raw) || raw == Enum.class) {
            return null;
        }
        // Anonymous enum subclasses (constants with a body) report the subclass here.
        if (!raw.isEnum()) {
            raw = raw.getSuperclass();
        }
        return (TypeAdapter<T>) new LowercaseEnumAdapter<>((Class<? extends Enum>) raw);
    }

    private static final class LowercaseEnumAdapter<E extends Enum<E>> extends TypeAdapter<E> {

        private final Map<String, E> byLowercaseName = new HashMap<>();

        LowercaseEnumAdapter(Class<E> enumType) {
            for (E constant : enumType.getEnumConstants()) {
                byLowercaseName.put(
                    constant.name()
                        .toLowerCase(java.util.Locale.ROOT),
                    constant);
            }
        }

        @Override
        public void write(JsonWriter out, E value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(
                    value.name()
                        .toLowerCase(java.util.Locale.ROOT));
            }
        }

        @Override
        public E read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return byLowercaseName.get(
                in.nextString()
                    .trim()
                    .toLowerCase(java.util.Locale.ROOT));
        }
    }
}
