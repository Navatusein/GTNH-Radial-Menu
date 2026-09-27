package com.navatusein.radialmenu.core.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A stored action: a type id plus loose string parameters.
 *
 * <p>
 * Deliberately data and nothing else - no behaviour, no Minecraft types. Executors live on the client side and are
 * looked up by {@link #type}. That keeps this package portable, and it means a new action type costs a descriptor and
 * an executor rather than a change here.
 *
 * <p>
 * {@link #steps} is the hook for action chains. A {@code sequence} action runs its steps in order; every other type
 * ignores the field. It is parsed from day one so that adding chains later cannot break existing files.
 */
public class ActionSpec {

    public String type;

    public Map<String, String> params;

    /** Nested actions, used by chain-style action types. */
    public List<ActionSpec> steps;

    /**
     * Which line a cycling command action will send next.
     *
     * <p>
     * Transient on purpose: it is where the player is in the cycle right now, not part of the configuration, and
     * persisting it would rewrite the profile file on every activation.
     */
    public transient int cycleCursor;

    public ActionSpec() {}

    public ActionSpec(String type) {
        this.type = type;
    }

    public Map<String, String> paramsOrEmpty() {
        if (params == null) {
            params = new LinkedHashMap<>();
        }
        return params;
    }

    public List<ActionSpec> stepsOrEmpty() {
        if (steps == null) {
            steps = new ArrayList<>();
        }
        return steps;
    }

    public ActionSpec set(String key, String value) {
        paramsOrEmpty().put(key, value);
        return this;
    }

    public String getString(String key, String fallback) {
        if (params == null) {
            return fallback;
        }
        String value = params.get(key);
        return value == null ? fallback : value;
    }

    public int getInt(String key, int fallback) {
        try {
            String value = getString(key, null);
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = getString(key, null);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    /** Case-insensitive enum lookup that falls back instead of throwing on a hand-edited typo. */
    public <E extends Enum<E>> E getEnum(String key, Class<E> enumType, E fallback) {
        String value = getString(key, null);
        if (value == null) {
            return fallback;
        }
        for (E constant : enumType.getEnumConstants()) {
            if (constant.name()
                .equalsIgnoreCase(value.trim())) {
                return constant;
            }
        }
        return fallback;
    }

    public ActionSpec copy() {
        ActionSpec copy = new ActionSpec(type);
        if (params != null) {
            copy.params = new LinkedHashMap<>(params);
        }
        if (steps != null) {
            copy.steps = new ArrayList<>(steps.size());
            for (ActionSpec step : steps) {
                copy.steps.add(step == null ? null : step.copy());
            }
        }
        return copy;
    }
}
