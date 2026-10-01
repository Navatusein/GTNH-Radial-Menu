package com.navatusein.radialmenu.core.script;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.jse.JseMathLib;

/**
 * The globals a script runs in.
 *
 * <p>
 * Built library by library rather than from {@code JsePlatform.standardGlobals()}, which is the difference between a
 * sandbox and a tidy-up: the file, process and module libraries are never loaded, so there is nothing to remove and
 * nothing a script can reach by a name we failed to think of. That is not protection from the profile's owner - they
 * wrote the script - but from a typo: a script is a macro, and a macro that can write files is a worse macro.
 *
 * <p>
 * {@link DebugLib} is the exception, and the ordering here is load-bearing. It is loaded because the interpreter only
 * honours an instruction hook when {@code globals.debuglib} is set, and that hook is the whole watchdog; the
 * {@code debug} table it installs is then removed, so a script cannot take the hook off itself.
 */
final class ScriptSandbox {

    private ScriptSandbox() {}

    static Globals newGlobals() {
        Globals globals = new Globals();

        // Every standard library registers itself in package.loaded on the way in, so something has to be there - and
        // the real PackageLib is exactly what must not be: it brings require, searchpath, and a searcher that loads
        // arbitrary Java classes. A bare table satisfies the registration and is taken away again below.
        LuaTable packages = new LuaTable();
        packages.set("loaded", new LuaTable());
        globals.set("package", packages);

        globals.load(new BaseLib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new CoroutineLib());
        globals.load(new JseMathLib());
        globals.load(new Bit32Lib());
        globals.load(new DebugLib());

        LoadState.install(globals);
        LuaC.install(globals);

        // The hook stays, the handle on it goes.
        globals.set("debug", LuaValue.NIL);

        // The registration table has done its job. Leaving it would be a map of the environment and, once PackageLib
        // is never loaded, of no use to anything else.
        globals.set("package", LuaValue.NIL);

        // BaseLib brings these two in; both read a chunk from somewhere else and run it, which is the one thing a
        // script embedded in a profile has no business doing.
        globals.set("dofile", LuaValue.NIL);
        globals.set("loadfile", LuaValue.NIL);

        return globals;
    }
}
