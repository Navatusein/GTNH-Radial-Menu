package com.navatusein.radialmenu.core.script;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import com.navatusein.radialmenu.core.action.ActionSpec;

/**
 * What a script can call.
 *
 * <p>
 * Every function here does the same thing: package the ask as a {@link ScriptRequest} and suspend. None of them touches
 * the game, and none of them can - that is the point of the split, not a limitation of it.
 *
 * <p>
 * Two of the documented functions are not here at all. {@code chat.await} and the public {@code menu.open} live in the
 * Lua prelude instead, because they need Lua values the host has no business holding: a pattern that only
 * {@code string.match} should interpret, an item table with the caller's own fields and callbacks on it. What crosses
 * into Java is a chat line, a projected list of labels, and an index.
 */
final class ScriptApi {

    private static final String PRELUDE = "/assets/radialmenu/scripts/prelude.lua";

    /**
     * Ticks a wait defaults to when the script names none. Three seconds - long enough for a server, short enough
     * that a wrong pattern is noticed rather than hung on.
     */
    private static final int DEFAULT_WAIT_TICKS = 60;

    private ScriptApi() {}

    static void install(final ScriptTask task, Globals globals) {
        LuaTable chat = new LuaTable();
        chat.set("send", new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs args) {
                task.yieldRequest(ScriptRequest.send(args.checkjstring(1)));
                return LuaValue.NONE;
            }
        });
        // The host-side half of chat.await: one line, and how long it took to arrive.
        chat.set("line", new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs args) {
                return task.yieldRequest(ScriptRequest.awaitLine(args.optint(1, DEFAULT_WAIT_TICKS)));
            }
        });
        globals.set("chat", chat);

        LuaTable menu = new LuaTable();
        menu.set("open", new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs args) {
                return task.yieldRequest(
                    ScriptRequest.menu(
                        ScriptMenus
                            .build(args.checktable(1), args.arg(2), task.token(), task.context(), task.limits())));
            }
        });
        menu.set("update", new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs args) {
                return task.yieldRequest(
                    ScriptRequest.menuUpdate(
                        ScriptMenus
                            .build(args.checktable(1), args.arg(2), task.token(), task.context(), task.limits())));
            }
        });
        menu.set("close", new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs args) {
                task.yieldRequest(ScriptRequest.menuClose());
                return LuaValue.NONE;
            }
        });
        // Told rather than discovered: the prelude can then say so when a list will not fit, instead of the wheel
        // quietly showing the first two dozen of thirty homes.
        menu.set("maxEntries", LuaValue.valueOf(task.limits().maxMenuItems));
        globals.set("menu", menu);

        globals.set("sleep", new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs args) {
                task.yieldRequest(ScriptRequest.sleep(args.optint(1, 1)));
                return LuaValue.NONE;
            }
        });

        globals.set("notify", new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs args) {
                task.yieldRequest(ScriptRequest.notify(join(args)));
                return LuaValue.NONE;
            }
        });

        LuaValue log = new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs args) {
                task.yieldRequest(ScriptRequest.log(join(args)));
                return LuaValue.NONE;
            }
        };
        globals.set("log", log);
        // Vanilla Lua's print writes to stdout, which in a Minecraft client is nobody's idea of a log. Pointing it at
        // ours means a script written the ordinary way still says something findable.
        globals.set("print", log);

        LuaTable action = new LuaTable();
        action.set("run", new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs args) {
                task.yieldRequest(ScriptRequest.action(specOf(args.checktable(1))));
                return LuaValue.NONE;
            }
        });
        globals.set("action", action);

        globals.set("chunk", chunkTable());

        globals.set("player", playerTable(task));

        globals.load(prelude(), "radialmenu-prelude")
            .call();
    }

    /**
     * Converting between a world coordinate and the chunk it lives in.
     *
     * <p>
     * The only functions a script can call that do not suspend, because they are the only ones that ask the game
     * nothing: a chunk coordinate is arithmetic on a number the script already has. They return there and then, and
     * a script may use them in a loop without spending a tick on each turn.
     */
    private static LuaTable chunkTable() {
        LuaTable chunk = new LuaTable();
        chunk.set("of", new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(ChunkCoords.chunkOf(args.checkint(1)));
            }
        });
        chunk.set("offset", new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(ChunkCoords.offsetOf(args.checkint(1)));
            }
        });
        chunk.set("toWorld", new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(ChunkCoords.toWorld(args.checkint(1), args.optint(2, 0)));
            }
        });
        chunk.set("size", LuaValue.valueOf(ChunkCoords.SIZE));
        return chunk;
    }

    /**
     * Reads the player's position when the script looks at it, through a metatable rather than as fixed fields.
     *
     * <p>
     * A script can be alive across a teleport - that is rather the point of one - so values copied in at launch would
     * be a lie by the time they were used. That goes double for where the player is looking, which changes with every
     * mouse movement rather than only with a teleport.
     *
     * <p>
     * Everything derived - the chunk, the compass direction - is worked out here from what the context already
     * answers, rather than added to the context. The host's job is to say where the player is; turning that into a
     * chunk is arithmetic, and arithmetic belongs on this side of the line where a test can reach it.
     */
    private static LuaTable playerTable(final ScriptTask task) {
        LuaTable player = new LuaTable();
        LuaTable meta = new LuaTable();
        meta.set("__index", new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs args) {
                ScriptContext context = task.context();
                if (context == null) {
                    return LuaValue.NIL;
                }
                String key = args.arg(2)
                    .tojstring();
                if ("name".equals(key)) {
                    return LuaValue.valueOf(context.playerName() == null ? "" : context.playerName());
                }
                if ("dim".equals(key)) {
                    return LuaValue.valueOf(context.dimension());
                }
                if ("x".equals(key)) {
                    return LuaValue.valueOf(context.blockX());
                }
                if ("y".equals(key)) {
                    return LuaValue.valueOf(context.blockY());
                }
                if ("z".equals(key)) {
                    return LuaValue.valueOf(context.blockZ());
                }
                // Which chunk, and where in it - the same arithmetic the chunk table offers a script for any
                // other coordinate, so the two can never answer differently about the same block.
                if ("chunkX".equals(key)) {
                    return LuaValue.valueOf(ChunkCoords.chunkOf(context.blockX()));
                }
                if ("chunkZ".equals(key)) {
                    return LuaValue.valueOf(ChunkCoords.chunkOf(context.blockZ()));
                }
                if ("xInChunk".equals(key)) {
                    return LuaValue.valueOf(ChunkCoords.offsetOf(context.blockX()));
                }
                if ("zInChunk".equals(key)) {
                    return LuaValue.valueOf(ChunkCoords.offsetOf(context.blockZ()));
                }
                // Normalised on the way out: a player who has turned around three times carries a yaw in the
                // hundreds, and a script comparing one against a number should not have to know that.
                if ("yaw".equals(key)) {
                    return LuaValue.valueOf(Facing.normalizeYaw(context.yaw()));
                }
                if ("pitch".equals(key)) {
                    return LuaValue.valueOf(context.pitch());
                }
                if ("facing".equals(key)) {
                    return LuaValue.valueOf(Facing.of(context.yaw()));
                }
                return LuaValue.NIL;
            }
        });
        player.setmetatable(meta);
        return player;
    }

    /**
     * Reads an action out of a Lua table.
     *
     * <p>
     * Parameters are strings everywhere else in the format, because that is what a stored profile holds; a script
     * writes
     * {@code holdTicks = 20} and means the same thing, so numbers and booleans are converted rather than refused.
     */
    private static ActionSpec specOf(LuaValue table) {
        ActionSpec spec = new ActionSpec(
            table.get("type")
                .optjstring(null));

        LuaValue params = table.get("params");
        if (params.istable()) {
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = params.next(key);
                key = next.arg1();
                if (key.isnil()) {
                    break;
                }
                spec.set(
                    key.tojstring(),
                    next.arg(2)
                        .tojstring());
            }
        }

        LuaValue steps = table.get("steps");
        if (steps.istable()) {
            for (int i = 1; i <= steps.length(); i++) {
                LuaValue step = steps.get(i);
                if (step.istable()) {
                    spec.stepsOrEmpty()
                        .add(specOf(step));
                }
            }
        }
        return spec;
    }

    private static String join(Varargs args) {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= args.narg(); i++) {
            if (i > 1) {
                text.append(' ');
            }
            text.append(
                args.arg(i)
                    .tojstring());
        }
        return text.toString();
    }

    /**
     * The prelude, from the jar.
     *
     * <p>
     * A missing one is a packaging fault rather than a user error, so it throws instead of leaving a script to fail
     * later
     * with "menu.open is nil" - which would send whoever hit it looking in entirely the wrong place.
     */
    private static String prelude() {
        InputStream stream = ScriptApi.class.getResourceAsStream(PRELUDE);
        if (stream == null) {
            throw new IllegalStateException("The script prelude is missing from the jar: " + PRELUDE);
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                bytes.write(buffer, 0, read);
            }
            return new String(bytes.toByteArray(), "UTF-8");
        } catch (IOException e) {
            throw new IllegalStateException("The script prelude could not be read", e);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Nothing useful to do about a stream that will not close.
            }
        }
    }
}
