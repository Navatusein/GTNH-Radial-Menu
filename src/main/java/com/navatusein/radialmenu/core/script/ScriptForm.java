package com.navatusein.radialmenu.core.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Several questions asked at once: what {@code prompt} becomes when it is handed a table.
 *
 * <p>
 * Plain data, built from the script's table by {@link ScriptApi} and drawn by the host. Everything that decides what an
 * answer means - whether it is a number, whether it is in range, what a ticked box is worth - lives here rather than in
 * the screen, so the screen only reports what was typed and the rules are a unit test.
 *
 * <p>
 * Answers cross back as strings, one per field, exactly as the controls hold them; {@link Field#check} says whether
 * one is acceptable and {@link Field#value} turns it into what the script is given.
 */
public final class ScriptForm {

    /** What a field asks for. */
    public enum Type {

        /** A line of text. */
        TEXT,

        /** A number, typed. */
        NUMBER,

        /** Yes or no, as a checkbox. */
        TOGGLE,

        /** One of a fixed list, as a button that steps through it. */
        CHOICE
    }

    /** Why an answer was not accepted. The host words it; this only names it. */
    public enum Problem {

        /** A required field left blank. */
        EMPTY,

        /** Something that does not read as a number. */
        NOT_A_NUMBER,

        /** A fraction where a whole number was asked for. */
        NOT_WHOLE,

        /** Below {@link Field#min}. */
        TOO_SMALL,

        /** Above {@link Field#max}. */
        TOO_LARGE
    }

    /** One entry of a {@link Type#CHOICE}: the key the script is given back, and what the button says. */
    public static final class Option {

        public final String key;
        public final String label;

        public Option(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    public static final class Field {

        public final String key;
        public final String label;

        /** A line under the control, or null for none. */
        public final String description;

        public final Type type;

        /**
         * What the control starts out holding, in answer form: the text, the number as typed, {@code "true"} or
         * {@code "false"}, or an option's key.
         */
        public final String initial;

        /**
         * Text: must not be blank. Number: must be given - when false, a blank number is no answer, and the field is
         * left out of the result rather than failing.
         */
        public final boolean required;

        /** Number bounds, inclusive. Null for none. */
        public final Double min;
        public final Double max;

        /** Number: fractions refused. */
        public final boolean integer;

        /** Choice: what can be picked, in order. Empty for every other type. */
        public final List<Option> options;

        public Field(String key, String label, String description, Type type, String initial, boolean required,
            Double min, Double max, boolean integer, List<Option> options) {
            this.key = key;
            this.label = label;
            this.description = description;
            this.type = type;
            this.initial = initial;
            this.required = required;
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.options = options == null ? Collections.<Option>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(options));
        }

        /** Whether this field can be filled in wrongly, which is what earns it a line for the complaint. */
        public boolean canFail() {
            return type == Type.NUMBER || (type == Type.TEXT && required);
        }

        /** @return null when the answer is acceptable */
        public Problem check(String answer) {
            String text = answer == null ? "" : answer.trim();
            switch (type) {
                case TEXT:
                    return required && text.isEmpty() ? Problem.EMPTY : null;
                case NUMBER:
                    if (text.isEmpty()) {
                        return required ? Problem.EMPTY : null;
                    }
                    Double number = parseNumber(text);
                    if (number == null) {
                        return Problem.NOT_A_NUMBER;
                    }
                    if (integer && number != Math.floor(number)) {
                        return Problem.NOT_WHOLE;
                    }
                    if (min != null && number < min) {
                        return Problem.TOO_SMALL;
                    }
                    if (max != null && number > max) {
                        return Problem.TOO_LARGE;
                    }
                    return null;
                default:
                    return null;
            }
        }

        /**
         * What the script is given for an accepted answer: a {@link String}, a {@link Double}, a {@link Boolean}, or
         * null for a number nobody had to give.
         */
        public Object value(String answer) {
            String text = answer == null ? "" : answer;
            switch (type) {
                case NUMBER:
                    return text.trim()
                        .isEmpty() ? null : parseNumber(text.trim());
                case TOGGLE:
                    return Boolean.valueOf("true".equals(text));
                case CHOICE:
                    return optionIndex(text) >= 0 ? text : (options.isEmpty() ? null : options.get(0).key);
                default:
                    return text;
            }
        }

        /** Where an option's key sits in {@link #options}, or -1. */
        public int optionIndex(String key) {
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).key.equals(key)) {
                    return i;
                }
            }
            return -1;
        }
    }

    public final String title;

    /** A paragraph above the fields, or null. */
    public final String description;

    public final List<Field> fields;

    public ScriptForm(String title, String description, List<Field> fields) {
        this.title = title;
        this.description = description;
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
    }

    /** The answers the controls start with, keyed by field. */
    public Map<String, String> initialAnswers() {
        Map<String, String> answers = new LinkedHashMap<>();
        for (Field field : fields) {
            answers.put(field.key, field.initial);
        }
        return answers;
    }

    /**
     * Every field whose answer is not acceptable, in the order they are drawn.
     *
     * @return empty when the form may be submitted
     */
    public Map<String, Problem> check(Map<String, String> answers) {
        Map<String, Problem> problems = new LinkedHashMap<>();
        for (Field field : fields) {
            Problem problem = field.check(answers.get(field.key));
            if (problem != null) {
                problems.put(field.key, problem);
            }
        }
        return problems;
    }

    /**
     * Reads a number the way a player types one.
     *
     * <p>
     * A comma is a decimal point: half the players who will fill this in write one, and nothing else a comma could
     * mean in a single number is worth refusing them for. Digits only otherwise - {@code parseDouble} on its own
     * also takes {@code 2f}, {@code 0x1p3} and {@code Infinity}, and no player typing those means a number.
     */
    static Double parseNumber(String text) {
        if (!NUMBER.matcher(text)
            .matches()) {
            return null;
        }
        return Double.valueOf(text.replace(',', '.'));
    }

    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+([.,]\\d*)?|[.,]\\d+)");

    /** A number as it should appear in a box: no {@code .0} on a whole one. */
    public static String formatNumber(double number) {
        if (number == Math.rint(number) && Math.abs(number) < 1e15) {
            return Long.toString((long) number);
        }
        return Double.toString(number);
    }
}
