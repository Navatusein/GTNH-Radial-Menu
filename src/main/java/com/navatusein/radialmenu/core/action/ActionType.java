package com.navatusein.radialmenu.core.action;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Editor-facing description of an action type: its id, its label and the parameters it accepts. */
public class ActionType {

    public final String id;

    /** Translation key for the type's display name. */
    public final String labelKey;

    public final List<ActionField> fields;

    /** Whether this type runs the nested {@link ActionSpec#steps} rather than its own parameters. */
    public final boolean chain;

    public ActionType(String id, String labelKey, boolean chain, ActionField... fields) {
        this.id = id;
        this.labelKey = labelKey;
        this.chain = chain;
        this.fields = Collections.unmodifiableList(Arrays.asList(fields));
    }

    public ActionField field(String key) {
        for (ActionField field : fields) {
            if (field.key.equals(key)) {
                return field;
            }
        }
        return null;
    }

    /** Writes every missing parameter's default into the spec, so the editor never shows a blank it cannot explain. */
    public void applyDefaults(ActionSpec spec) {
        for (ActionField field : fields) {
            if (spec.getString(field.key, null) == null) {
                spec.set(field.key, field.defaultValue);
            }
        }
    }

    public ActionSpec newSpec() {
        ActionSpec spec = new ActionSpec(id);
        applyDefaults(spec);
        return spec;
    }
}
