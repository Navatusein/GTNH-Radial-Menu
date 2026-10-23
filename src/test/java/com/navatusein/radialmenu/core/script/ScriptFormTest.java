package com.navatusein.radialmenu.core.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * {@code prompt} handed a table: several questions on one screen, and answers that arrive as what they are.
 *
 * <p>
 * Driven through real scripts, because the contract is the table a script writes and the table it gets back - a key
 * read under a different spelling, or a number arriving as a string, is nil or a concatenation bug and nothing says so.
 */
public class ScriptFormTest {

    private static final String SIZE_FORM = "local size = prompt({\n" + "  title = \"Fill\",\n"
        + "  description = \"How many chunks\",\n"
        + "  fields = {\n"
        + "    { key = \"forward\", label = \"Forward\", type = \"number\", default = 2, min = 1, integer = true },\n"
        + "    { key = \"side\", label = \"Sideways\", type = \"number\", default = 3 },\n"
        + "    { key = \"replace\", label = \"Replace blocks\", type = \"toggle\" },\n"
        + "    { key = \"towards\", label = \"Towards\", type = \"choice\", options = {\n"
        + "      { key = \"right\", label = \"Right\" },\n"
        + "      { key = \"left\", label = \"Left\" } } },\n"
        + "    { key = \"name\", label = \"Name\" }\n"
        + "  }\n"
        + "})\n";

    private final FakeScriptHost host = new FakeScriptHost();

    @Test
    public void untouchedFieldsAnswerWithTheirDefaults() {
        host.filled.add(Collections.<String, String>emptyMap());

        assertEquals(
            Arrays.asList("6", "false", "right", "[]"),
            notices(
                SIZE_FORM + "notify(size.forward * size.side) notify(tostring(size.replace)) notify(size.towards) "
                    + "notify(\"[\" .. size.name .. \"]\")"));
    }

    @Test
    public void answersArriveAsWhatTheirFieldPromises() {
        host.filled.add(answers("forward", "4", "side", "-2,5", "replace", "true", "towards", "left", "name", "farm"));

        assertEquals(
            Arrays.asList("number", "4", "-2.5", "boolean", "true", "left", "farm"),
            notices(
                SIZE_FORM + "notify(type(size.forward)) notify(size.forward) notify(size.side) "
                    + "notify(type(size.replace)) notify(tostring(size.replace)) notify(size.towards) "
                    + "notify(size.name)"));
    }

    @Test
    public void theFormCarriesWhatTheScriptAsked() {
        host.filled.add(Collections.<String, String>emptyMap());
        host.run(SIZE_FORM);

        ScriptForm form = host.forms.get(0);
        assertEquals("Fill", form.title);
        assertEquals("How many chunks", form.description);
        assertEquals(5, form.fields.size());

        ScriptForm.Field forward = form.fields.get(0);
        assertEquals(ScriptForm.Type.NUMBER, forward.type);
        assertEquals("2", forward.initial);
        assertEquals(Double.valueOf(1), forward.min);
        assertTrue(forward.integer);
        assertTrue(forward.required);

        ScriptForm.Field towards = form.fields.get(3);
        assertEquals(ScriptForm.Type.CHOICE, towards.type);
        assertEquals("right", towards.initial);
        assertEquals("Left", towards.options.get(1).label);

        // A field with no type is text, and text may be left blank unless the script says otherwise.
        assertEquals(ScriptForm.Type.TEXT, form.fields.get(4).type);
        assertEquals(false, form.fields.get(4).required);
    }

    @Test
    public void cancellingIsNil() {
        assertEquals(Arrays.asList("cancelled"), notices(SIZE_FORM + "if size == nil then notify(\"cancelled\") end"));
    }

    @Test
    public void anOptionalNumberLeftBlankIsLeftOut() {
        host.filled.add(answers("radius", ""));

        assertEquals(
            Arrays.asList("no radius"),
            notices(
                "local a = prompt({ fields = { { key = \"radius\", type = \"number\", required = false } } }) "
                    + "if a.radius == nil then notify(\"no radius\") end"));
    }

    @Test
    public void aLabelIsKeyEnoughAndAStringIsAnOption() {
        host.filled.add(answers("Mode", "fast"));

        assertEquals(
            Arrays.asList("fast"),
            notices(
                "local a = prompt({ fields = { { label = \"Mode\", type = \"choice\", "
                    + "options = { \"slow\", \"fast\" } } } }) notify(a.Mode)"));
        assertEquals("slow", host.forms.get(0).fields.get(0).options.get(0).label);
    }

    @Test
    public void theOldOneLinePromptStillAnswersWithAString() {
        host.typed.add("riverside");

        assertEquals(
            Arrays.asList("string riverside"),
            notices("local n = prompt(\"Name\") notify(type(n) .. \" \" .. n)"));
        assertTrue(host.forms.isEmpty());
    }

    @Test
    public void aMistakeInTheTableIsAnErrorNamingTheField() {
        assertError("unknown type 'nmber'", "prompt({ fields = { { key = \"n\", type = \"nmber\" } } })");
        assertError("needs 'options'", "prompt({ fields = { { key = \"c\", type = \"choice\" } } })");
        assertError("two fields have the key 'a'", "prompt({ fields = { { key = \"a\" }, { label = \"a\" } } })");
        assertError(
            "is not one of its options",
            "prompt({ fields = { { key = \"c\", type = \"choice\", options = { \"x\" }, default = \"y\" } } })");
        assertError("needs a key or a label", "prompt({ fields = { { type = \"text\" } } })");
        assertError("at least one field", "prompt({ title = \"Empty\" })");
        assertError(
            "min is above max",
            "prompt({ fields = { { key = \"n\", type = \"number\", min = 5, max = 1 } } })");
        assertError(
            "default must be true or false",
            "prompt({ fields = { { key = \"t\", type = \"toggle\", default = \"yes\" } } })");
    }

    @Test
    public void numbersAreReadTheWayAPlayerTypesThem() {
        ScriptForm.Field number = numberField(null, null, false);

        assertNull(number.check("12"));
        assertNull(number.check(" -3 "));
        assertNull(number.check("2,5"));
        assertNull(number.check(".5"));
        assertEquals(Double.valueOf(2.5), number.value("2,5"));

        // Things parseDouble takes and no player means as a number.
        assertEquals(ScriptForm.Problem.NOT_A_NUMBER, number.check("2f"));
        assertEquals(ScriptForm.Problem.NOT_A_NUMBER, number.check("0x10"));
        assertEquals(ScriptForm.Problem.NOT_A_NUMBER, number.check("Infinity"));
        assertEquals(ScriptForm.Problem.NOT_A_NUMBER, number.check("two"));
        assertEquals(ScriptForm.Problem.EMPTY, number.check("  "));
    }

    @Test
    public void boundsAndWholenessAreChecked() {
        ScriptForm.Field number = numberField(1.0, 64.0, true);

        assertNull(number.check("1"));
        assertNull(number.check("64"));
        assertEquals(ScriptForm.Problem.TOO_SMALL, number.check("0"));
        assertEquals(ScriptForm.Problem.TOO_LARGE, number.check("65"));
        assertEquals(ScriptForm.Problem.NOT_WHOLE, number.check("1.5"));
    }

    @Test
    public void aRequiredTextMustNotBeBlank() {
        ScriptForm.Field text = new ScriptForm.Field(
            "name",
            "Name",
            null,
            ScriptForm.Type.TEXT,
            "",
            true,
            null,
            null,
            false,
            null);

        assertEquals(ScriptForm.Problem.EMPTY, text.check("   "));
        assertNull(text.check("farm"));
    }

    @Test
    public void aWholeNumberIsWrittenWithoutAFraction() {
        assertEquals("3", ScriptForm.formatNumber(3.0));
        assertEquals("-12", ScriptForm.formatNumber(-12.0));
        assertEquals("2.5", ScriptForm.formatNumber(2.5));
    }

    private static ScriptForm.Field numberField(Double min, Double max, boolean integer) {
        return new ScriptForm.Field("n", "N", null, ScriptForm.Type.NUMBER, "", true, min, max, integer, null);
    }

    private void assertError(String expected, String source) {
        ScriptTask task = new FakeScriptHost().run(source);
        String error = task.error();
        assertNotNull("expected an error mentioning: " + expected, error);
        assertTrue(error + " does not mention: " + expected, error.contains(expected));
    }

    private static Map<String, String> answers(String... pairs) {
        Map<String, String> answers = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            answers.put(pairs[i], pairs[i + 1]);
        }
        return answers;
    }

    private List<String> notices(String source) {
        host.notices.clear();
        host.run(source);
        return host.notices;
    }
}
