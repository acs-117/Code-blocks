package com.example.codeblocks;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MainActivity extends AppCompatActivity {

    /* ============================================================
       1. BLOCKS
       ============================================================ */

    static final String NONE = "(no variable)";
    static final int ASK = 0, SET = 1, PRINT = 2, IF = 3, FOR = 4;
    static final String[] KIND_EMOJI = {"📥", "📦", "💬", "❓", "🔁"};
    static final String[] KIND_NAME = {"Ask", "Set", "Print", "If", "Repeat"};
    static final int[] KIND_COLOR = {0xFF1A9FD6, 0xFFFF8A3D, 0xFF8E6BFF, 0xFF22B573, 0xFFFF5C8A};
    static final String[] OPS = {"=", "≠", ">", "<", "≥", "≤", "is divisible by"};

    static class Block {
        int kind;
        String name = "";       // ASK / SET / FOR: variable name (typed)
        String value = "";      // SET: value (typed)
        String text = "";       // PRINT: text (typed)
        String variable = "";   // PRINT: variable to show / IF: variable to test
        String op = "=";        // IF
        String right = "";      // IF: value to compare with (typed)
        String from = "1";      // FOR
        String to = "5";        // FOR
        List<Block> thenBody = new ArrayList<>();
        List<Block> elseBody = new ArrayList<>();
        List<Block> body = new ArrayList<>();

        Block(int kind) { this.kind = kind; }
    }

    static Block newBlock(int kind) {
        Block b = new Block(kind);
        if (kind == PRINT) b.variable = NONE;
        if (kind == FOR) b.name = "i";
        return b;
    }

    /* ============================================================
       2. LEVELS
       ============================================================ */

    static class TestCase {
        List<String> inputs, expected;
        TestCase(List<String> inputs, List<String> expected) {
            this.inputs = inputs;
            this.expected = expected;
        }
    }

    static class Level {
        String title, task, concept, hint;
        int[] kinds;
        List<TestCase> tests;
        Level(String title, String task, String concept, String hint, int[] kinds, List<TestCase> tests) {
            this.title = title; this.task = task; this.concept = concept;
            this.hint = hint; this.kinds = kinds; this.tests = tests;
        }
    }

    static List<Level> buildLevels() {
        List<Level> list = new ArrayList<>();

        list.add(new Level(
                "Say Hello",
                "Ask for a name, then print Hello and the name.",
                "ASK gets a value while the program runs. A VARIABLE is a named box that remembers it. PRINT shows it.",
                "Ask for a variable called name. Then Print the text Hello and pick name in the dropdown.",
                new int[]{ASK, SET, PRINT},
                Arrays.asList(
                        new TestCase(Arrays.asList("Asha"), Arrays.asList("Hello Asha")),
                        new TestCase(Arrays.asList("Ravi"), Arrays.asList("Hello Ravi")),
                        new TestCase(Arrays.asList("Sam"), Arrays.asList("Hello Sam")))));

        list.add(new Level(
                "Odd or Even",
                "Ask for a number. Print Even if it is even, otherwise print Odd.",
                "IF / OTHERWISE lets a program choose. An even number is divisible by 2.",
                "Ask for n. Add an If: n is divisible by 2. Put Print Even in the top box and Print Odd under 'otherwise'.",
                new int[]{ASK, SET, PRINT, IF},
                Arrays.asList(
                        new TestCase(Arrays.asList("4"), Arrays.asList("Even")),
                        new TestCase(Arrays.asList("7"), Arrays.asList("Odd")),
                        new TestCase(Arrays.asList("10"), Arrays.asList("Even")),
                        new TestCase(Arrays.asList("13"), Arrays.asList("Odd")))));

        List<String> oneToTen = new ArrayList<>();
        for (int i = 1; i <= 10; i++) oneToTen.add(String.valueOf(i));
        list.add(new Level(
                "Count to 10",
                "Print the numbers 1 to 10, one per line.",
                "A REPEAT (for) loop runs the blocks inside it again and again. The variable i counts for you.",
                "Use Repeat from 1 to 10. Put a Print inside it and pick i in the variable dropdown.",
                new int[]{SET, PRINT, FOR},
                Arrays.asList(new TestCase(new ArrayList<String>(), oneToTen))));

        return list;
    }

    /* ============================================================
       3. INTERPRETER
       ============================================================ */

    static class ProgramError extends RuntimeException {
        ProgramError(String m) { super(m); }
    }

    static class Stop extends RuntimeException { }

    static Double num(String s) {
        try { return Double.valueOf(s.trim()); } catch (Exception e) { return null; }
    }

    static boolean compare(String l, String op, String r) {
        Double a = num(l), c = num(r);
        if (op.equals("is divisible by")) {
            if (a == null || c == null || c.intValue() == 0) return false;
            return a.longValue() % c.longValue() == 0;
        }
        int cmp = (a != null && c != null) ? Double.compare(a, c) : l.trim().compareToIgnoreCase(r.trim());
        switch (op) {
            case "=": return cmp == 0;
            case "≠": return cmp != 0;
            case ">": return cmp > 0;
            case "<": return cmp < 0;
            case "≥": return cmp >= 0;
            case "≤": return cmp <= 0;
            default: return false;
        }
    }

    static class Runner {
        interface IO {
            String read(String name);
            void print(String line);
        }

        final IO io;
        final Map<String, String> vars = new HashMap<>();
        int steps = 0;

        Runner(IO io) { this.io = io; }

        void tick() {
            if (Thread.currentThread().isInterrupted()) throw new Stop();
            if (++steps > 5000) throw new ProgramError("Too many steps - is a loop running forever?");
        }

        String needName(String n) {
            String t = n.trim();
            if (t.isEmpty()) throw new ProgramError("A block is missing its variable name.");
            return t;
        }

        String get(String n) {
            String v = vars.get(n.trim());
            if (v == null) throw new ProgramError("\"" + n + "\" has no value yet. Use Ask or Set before this block.");
            return v;
        }

        String resolve(String s) {
            String t = s.trim();
            return vars.containsKey(t) ? vars.get(t) : s;
        }

        int toInt(String s) {
            Double d = num(resolve(s));
            if (d == null) throw new ProgramError("Repeat needs numbers, but got \"" + s + "\".");
            return d.intValue();
        }

        void run(List<Block> list) {
            for (Block b : new ArrayList<>(list)) {
                tick();
                switch (b.kind) {
                    case ASK: {
                        String n = needName(b.name);
                        vars.put(n, io.read(n));
                        break;
                    }
                    case SET: {
                        vars.put(needName(b.name), b.value);
                        break;
                    }
                    case PRINT: {
                        String out = b.text;
                        if (!b.variable.isEmpty() && !b.variable.equals(NONE)) {
                            String v = get(b.variable);
                            out = out.isEmpty() ? v : out + " " + v;
                        }
                        io.print(out);
                        break;
                    }
                    case IF: {
                        if (b.variable.isEmpty()) throw new ProgramError("Pick a variable in your If block.");
                        if (compare(get(b.variable), b.op, resolve(b.right))) run(b.thenBody);
                        else run(b.elseBody);
                        break;
                    }
                    case FOR: {
                        String n = needName(b.name);
                        int from = toInt(b.from), to = toInt(b.to);
                        for (int k = from; k <= to; k++) {
                            tick();
                            vars.put(n, String.valueOf(k));
                            run(b.body);
                        }
                        break;
                    }
                }
            }
        }
    }

    static String norm(String s) { return s.trim().replaceAll("\\s+", " ").toLowerCase(); }

    static class Result {
        List<String> inputs, expected, actual;
        String error;
        Result(List<String> i, List<String> e, List<String> a, String err) {
            inputs = i; expected = e; actual = a; error = err;
        }
        boolean passed() {
            if (error != null || expected.size() != actual.size()) return false;
            for (int i = 0; i < expected.size(); i++) {
                if (!norm(expected.get(i)).equals(norm(actual.get(i)))) return false;
            }
            return true;
        }
    }

    /** Silent run against every test case (no popups). */
    static List<Result> runTests(Level lv, List<Block> prog) {
        List<Result> results = new ArrayList<>();
        for (TestCase t : lv.tests) {
            final List<String> out = new ArrayList<>();
            final List<String> in = t.inputs;
            final int[] idx = {0};
            Runner r = new Runner(new Runner.IO() {
                public String read(String name) { return idx[0] < in.size() ? in.get(idx[0]++) : ""; }
                public void print(String line) { out.add(line); }
            });
            try {
                r.run(prog);
                results.add(new Result(t.inputs, t.expected, out, null));
            } catch (ProgramError e) {
                results.add(new Result(t.inputs, t.expected, out, e.getMessage()));
            } catch (RuntimeException e) {
                results.add(new Result(t.inputs, t.expected, out, "Something went wrong."));
            }
        }
        return results;
    }

    static boolean allPassed(List<Result> rs) {
        for (Result r : rs) if (!r.passed()) return false;
        return true;
    }

    /* ============================================================
       4. UI STATE
       ============================================================ */

    static class Slot {
        View view;
        List<Block> list;
        List<View> children = new ArrayList<>();
        Slot(View v, List<Block> l) { view = v; list = l; }
    }

    final Typeface TITLE_FONT = Typeface.create("sans-serif-black", Typeface.NORMAL);
    final Typeface BODY_FONT = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    final List<Level> levels = buildLevels();
    final boolean[] done = new boolean[levels.size()];
    int current = -1;
    List<Block> program = new ArrayList<>();

    boolean running;
    int runId;
    Thread runThread;
    AlertDialog activeDialog;
    String outputText = "";
    String errorText = null;
    List<Result> results = null;

    FrameLayout root;
    View dropLine, trayView;
    ScrollView workScroll, panelScroll;
    LinearLayout workCol, panelCol;
    TextView runBtn;
    Drawable trayNormal, trayHot;
    OnBackPressedCallback backCallback;
    final Handler handler = new Handler(Looper.getMainLooper());

    final List<Slot> slots = new ArrayList<>();
    boolean dragging;
    float lastDragX, lastDragY;
    Object lastState;
    List<Block> targetList;
    int targetIndex;
    boolean overTray;

    /* ============================================================
       5. ACTIVITY + SCREENS
       ============================================================ */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF2B2370, 0xFF14123A}));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            if (trayView != null) {
                trayView.setVisibility(insets.isVisible(WindowInsetsCompat.Type.ime()) ? View.GONE : View.VISIBLE);
            }
            return insets;
        });
        setContentView(root);

        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), root);
        c.setAppearanceLightStatusBars(false);
        c.setAppearanceLightNavigationBars(false);

        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() { showMenu(); }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);

        showMenu();
    }

    @Override
    protected void onDestroy() {
        stopRun();
        super.onDestroy();
    }

    /* ---------- Level menu ---------- */

    void showMenu() {
        stopRun();
        current = -1;
        trayView = null;
        backCallback.setEnabled(false);
        root.setOnDragListener(null);
        root.removeAllViews();

        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(24), dp(20), dp(24));
        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        add(col, text("🧩 Block Coder", 36, Color.WHITE, true));
        add(col, text("Pick a level and start coding!", 16, 0xFFC9C3FF, false));

        int[] tileColors = {0xFFFF8A3D, 0xFF8E6BFF, 0xFFFF5C8A};
        for (int i = 0; i < levels.size(); i++) {
            final int idx = i;
            Level lv = levels.get(i);
            boolean unlocked = (i == 0) || done[i - 1];
            String icon = done[i] ? "✅" : (unlocked ? "▶" : "🔒");

            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setBackground(block3d(unlocked ? tileColors[i % tileColors.length] : 0xFF5A557F, dp(16)));
            tile.setPadding(dp(18), dp(16), dp(18), dp(20));
            TextView t1 = text(icon + "  Level " + (i + 1) + " · " + lv.title, 20, Color.WHITE, true);
            TextView t2 = text(lv.task, 13, 0xDDFFFFFF, false);
            tile.addView(t1);
            tile.addView(t2);
            if (unlocked) tile.setOnClickListener(v -> showGame(idx));
            add(col, tile);
        }
    }

    /* ---------- Game screen ---------- */

    void showGame(int idx) {
        stopRun();
        current = idx;
        program = new ArrayList<>();
        outputText = "";
        errorText = null;
        results = null;
        backCallback.setEnabled(true);
        root.removeAllViews();

        Level lv = levels.get(idx);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);

        // top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        TextView back = pill("←", 0xFF5A557F, Color.WHITE, 16);
        back.setOnClickListener(v -> showMenu());
        bar.addView(back);
        TextView title = text("Level " + (idx + 1) + " · " + lv.title, 18, Color.WHITE, true);
        title.setPadding(dp(12), 0, dp(8), 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        runBtn = pill("▶ Run", 0xFF2ECC71, Color.WHITE, 16);
        runBtn.setOnClickListener(v -> toggleRun());
        bar.addView(runBtn);
        TextView reset = pill("↺", 0xFF5A557F, Color.WHITE, 16);
        reset.setOnClickListener(v -> resetProgram());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.leftMargin = dp(6);
        bar.addView(reset, rlp);
        screen.addView(bar);

        // workspace
        workScroll = new ScrollView(this);
        workCol = new LinearLayout(this);
        workCol.setOrientation(LinearLayout.VERTICAL);
        workCol.setPadding(dp(12), dp(4), dp(12), dp(24));
        workScroll.addView(workCol);
        screen.addView(workScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // output / results panel
        panelScroll = new ScrollView(this);
        panelScroll.setBackground(rounded(0xFF0B0A24, dp(12)));
        panelCol = new LinearLayout(this);
        panelCol.setOrientation(LinearLayout.VERTICAL);
        panelCol.setPadding(dp(12), dp(8), dp(12), dp(8));
        panelScroll.addView(panelCol);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(150));
        plp.setMargins(dp(10), dp(4), dp(10), dp(6));
        screen.addView(panelScroll, plp);

        // card tray
        trayView = buildTray(lv);
        screen.addView(trayView);

        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));

        dropLine = new View(this);
        dropLine.setBackground(rounded(0xFFFFD54F, dp(3)));
        dropLine.setVisibility(View.GONE);
        root.addView(dropLine, new FrameLayout.LayoutParams(dp(100), dp(5)));

        root.setOnDragListener(this::onDrag);

        updateRunButton();
        renderWorkspace();
        renderPanel();
    }

    /* ---------- Workspace: Start block + Lego stack ---------- */

    void renderWorkspace() {
        slots.clear();
        workCol.removeAllViews();
        Level lv = levels.get(current);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setBackground(rounded(0x22FFFFFF, dp(12)));
        info.setPadding(dp(12), dp(10), dp(12), dp(10));
        info.addView(text(lv.task, 16, Color.WHITE, true));
        info.addView(text("💡 " + lv.concept, 13, 0xFFC9C3FF, false));
        add(workCol, info);

        TextView start = text("▶  Start", 18, 0xFF3B2F00, true);
        start.setBackground(block3d(0xFFFFC933, dp(14)));
        start.setPadding(dp(16), dp(10), dp(16), dp(15));
        add(workCol, start);

        add(workCol, slotView(program, lv, true));
    }

    /** A box that holds a vertical stack of blocks. */
    View slotView(List<Block> list, Level lv, boolean isRoot) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setMinimumHeight(dp(isRoot ? 110 : 44));
        box.setPadding(dp(4), dp(4), dp(4), dp(4));
        box.setBackground(rounded(0x1AFFFFFF, dp(10)));

        Slot s = new Slot(box, list);
        slots.add(s);

        if (list.isEmpty()) {
            TextView hint = text(isRoot ? "Drag cards here ⬆" : "drop blocks here", 13, 0x88FFFFFF, false);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(isRoot ? 36 : 10), 0, dp(10));
            box.addView(hint);
        }
        for (Block b : new ArrayList<>(list)) {
            View v = blockView(b, list, lv);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(1);
            box.addView(v, lp);
            s.children.add(v);
        }
        return box;
    }

    /** One block (simple blocks) or one C-shaped block (If / Repeat) with an indented slot. */
    View blockView(final Block b, final List<Block> parent, final Level lv) {
        int color = KIND_COLOR[b.kind];

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(block3d(color, dp(10)));
        header.setPadding(dp(2), dp(6), dp(4), dp(9));

        TextView handle = text("≡", 24, 0xDDFFFFFF, true);
        handle.setPadding(dp(10), dp(2), dp(10), dp(2));
        attachHandleDrag(handle, card, b);
        header.addView(handle);

        FlowLayout controls = new FlowLayout(this, dp(6));
        header.addView(controls, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        switch (b.kind) {
            case ASK:
                controls.addView(label("📥 ask for"));
                controls.addView(field(b.name, "name", s -> b.name = s));
                break;
            case SET:
                controls.addView(label("📦 set"));
                controls.addView(field(b.name, "name", s -> b.name = s));
                controls.addView(label("to"));
                controls.addView(field(b.value, "value", s -> b.value = s));
                break;
            case PRINT:
                controls.addView(label("💬 print"));
                controls.addView(field(b.text, "text", s -> b.text = s));
                controls.addView(label("+"));
                controls.addView(picker(b.variable, NONE, () -> {
                    List<String> o = new ArrayList<>();
                    o.add(NONE);
                    o.addAll(names());
                    return o;
                }, s -> b.variable = s));
                break;
            case IF:
                controls.addView(label("❓ if"));
                controls.addView(picker(b.variable, "variable", () -> names(), s -> b.variable = s));
                controls.addView(picker(b.op, "=", () -> Arrays.asList(OPS), s -> b.op = s));
                controls.addView(field(b.right, "value", s -> b.right = s));
                break;
            case FOR:
                controls.addView(label("🔁 repeat"));
                controls.addView(field(b.name, "i", s -> b.name = s));
                controls.addView(label("from"));
                controls.addView(field(b.from, "1", s -> b.from = s));
                controls.addView(label("to"));
                controls.addView(field(b.to, "10", s -> b.to = s));
                break;
        }

        TextView x = text("✕", 16, 0xDDFFFFFF, true);
        x.setPadding(dp(10), dp(4), dp(8), dp(4));
        x.setOnClickListener(v -> {
            if (running) return;
            parent.remove(b);
            results = null;
            renderWorkspace();
        });
        header.addView(x);
        card.addView(header);

        if (b.kind == IF) {
            card.addView(cRow(color, slotView(b.thenBody, lv, false)));
            card.addView(labelBar("otherwise", color));
            card.addView(cRow(color, slotView(b.elseBody, lv, false)));
            card.addView(footer(color));
        } else if (b.kind == FOR) {
            card.addView(cRow(color, slotView(b.body, lv, false)));
            card.addView(footer(color));
        }
        return card;
    }

    /** Rail on the left (the indent) + the inner slot. */
    View cRow(int color, View slotBox) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        View rail = new View(this);
        rail.setBackgroundColor(color);
        row.addView(rail, new LinearLayout.LayoutParams(dp(20), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(slotBox, lp);
        return row;
    }

    View labelBar(String s, int color) {
        TextView t = text(s, 13, Color.WHITE, true);
        t.setBackgroundColor(color);
        t.setPadding(dp(26), dp(4), dp(10), dp(4));
        return t;
    }

    View footer(int color) {
        View f = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        float r = dp(10);
        g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        f.setBackground(g);
        f.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(14)));
        return f;
    }

    /* ---------- Card tray (bottom) ---------- */

    View buildTray(final Level lv) {
        LinearLayout tray = new LinearLayout(this);
        tray.setOrientation(LinearLayout.VERTICAL);
        tray.setPadding(dp(6), dp(8), dp(6), dp(8));
        trayNormal = trayBg(0xFF0E0C2E);
        trayHot = trayBg(0xFF7A1F2B);
        tray.setBackground(trayNormal);

        TextView hint = text("Swipe a card up into your program, or tap it. Drag a block back here to delete it.",
                12, 0xFFB9B3F5, false);
        hint.setGravity(Gravity.CENTER);
        tray.addView(hint);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(6), dp(4), dp(2));
        for (int kind : lv.kinds) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(96), dp(110));
            lp.setMargins(dp(5), 0, dp(5), 0);
            row.addView(trayCard(kind), lp);
        }
        hs.addView(row);
        tray.addView(hs);
        return tray;
    }

    Drawable trayBg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        float r = dp(18);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        g.setStroke(dp(2), 0x55FFC933);
        return g;
    }

    View trayCard(final int kind) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setBackground(block3d(KIND_COLOR[kind], dp(14)));
        c.setPadding(dp(6), dp(8), dp(6), dp(12));
        TextView e = text(KIND_EMOJI[kind], 34, Color.WHITE, false);
        e.setGravity(Gravity.CENTER);
        TextView n = text(KIND_NAME[kind], 15, Color.WHITE, true);
        n.setGravity(Gravity.CENTER);
        c.addView(e);
        c.addView(n);

        // tap = add to the end
        c.setOnClickListener(v -> {
            if (running) return;
            program.add(newBlock(kind));
            results = null;
            renderWorkspace();
            workScroll.post(() -> workScroll.fullScroll(View.FOCUS_DOWN));
        });

        // swipe up = start dragging (sideways swipes still scroll the tray)
        final float[] st = new float[3];
        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        c.setOnTouchListener((v, ev) -> {
            if (running) return false;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    st[0] = ev.getRawX();
                    st[1] = ev.getRawY();
                    st[2] = 0;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dx = ev.getRawX() - st[0], dy = ev.getRawY() - st[1];
                    if (st[2] == 0 && dy < -slop && Math.abs(dy) > Math.abs(dx)) {
                        st[2] = 1;
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        startDrag(v, kind);
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        });
        return c;
    }

    /* ---------- Dragging blocks that are already in the program ---------- */

    void attachHandleDrag(View handle, final View card, final Block b) {
        final float[] st = new float[3];
        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        handle.setOnTouchListener((v, ev) -> {
            if (running) return true;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    st[0] = ev.getRawX();
                    st[1] = ev.getRawY();
                    st[2] = 0;
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (st[2] == 0 && Math.hypot(ev.getRawX() - st[0], ev.getRawY() - st[1]) > slop) {
                        st[2] = 1;
                        startDrag(card, b);
                    }
                    return true;
                default:
                    return true;
            }
        });
    }

    void startDrag(View shadowOf, Object state) {
        ClipData cd = ClipData.newPlainText("block", "block");
        shadowOf.startDragAndDrop(cd, new View.DragShadowBuilder(shadowOf), state, 0);
    }

    boolean onDrag(View v, DragEvent e) {
        Object state = e.getLocalState();
        switch (e.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                if (!(state instanceof Block) && !(state instanceof Integer)) return false;
                dragging = true;
                lastState = state;
                handler.post(autoScroll);
                return true;
            case DragEvent.ACTION_DRAG_LOCATION: {
                int[] l = new int[2];
                v.getLocationOnScreen(l);
                updateDrag(l[0] + e.getX(), l[1] + e.getY(), state);
                return true;
            }
            case DragEvent.ACTION_DROP: {
                int[] l = new int[2];
                v.getLocationOnScreen(l);
                updateDrag(l[0] + e.getX(), l[1] + e.getY(), state);
                applyDrop(state);
                return true;
            }
            case DragEvent.ACTION_DRAG_ENDED:
                dragging = false;
                handler.removeCallbacks(autoScroll);
                if (dropLine != null) dropLine.setVisibility(View.GONE);
                setTrayHot(false);
                return true;
            default:
                return true;
        }
    }

    final Runnable autoScroll = new Runnable() {
        @Override
        public void run() {
            if (!dragging || workScroll == null) return;
            Rect sr = rectOf(workScroll);
            int edge = dp(70);
            int step = dp(10);
            if (lastDragY < sr.top + edge) {
                workScroll.scrollBy(0, -step);
                updateDrag(lastDragX, lastDragY, lastState);
            } else if (lastDragY > sr.bottom - edge && lastDragY <= sr.bottom) {
                workScroll.scrollBy(0, step);
                updateDrag(lastDragX, lastDragY, lastState);
            }
            handler.postDelayed(this, 16);
        }
    };

    /** Works out where the dragged block would land and shows the yellow drop line. */
    void updateDrag(float sx, float sy, Object state) {
        lastDragX = sx;
        lastDragY = sy;
        lastState = state;
        targetList = null;

        overTray = trayView != null && trayView.getVisibility() == View.VISIBLE
                && rectOf(trayView).contains((int) sx, (int) sy);
        setTrayHot(overTray && state instanceof Block);
        if (overTray || slots.isEmpty()) {
            dropLine.setVisibility(View.GONE);
            return;
        }
        if (!rectOf(workScroll).contains((int) sx, (int) sy)) {
            dropLine.setVisibility(View.GONE);
            return;
        }

        // deepest (smallest) slot under the finger
        Slot best = null;
        long bestArea = Long.MAX_VALUE;
        for (Slot s : slots) {
            if (state instanceof Block && containsList((Block) state, s.list)) continue;
            Rect r = rectOf(s.view);
            if (r.contains((int) sx, (int) sy)) {
                long area = (long) r.width() * r.height();
                if (area < bestArea) {
                    best = s;
                    bestArea = area;
                }
            }
        }
        if (best == null) best = slots.get(0);   // main stack

        int idx = best.children.size();
        for (int i = 0; i < best.children.size(); i++) {
            if (sy < rectOf(best.children.get(i)).centerY()) {
                idx = i;
                break;
            }
        }
        targetList = best.list;
        targetIndex = idx;

        Rect sv = rectOf(best.view);
        float ly;
        if (best.children.isEmpty()) ly = sv.top + dp(8);
        else if (idx < best.children.size()) ly = rectOf(best.children.get(idx)).top - dp(1);
        else ly = rectOf(best.children.get(best.children.size() - 1)).bottom + dp(1);
        showLine(sv.left + dp(6), ly - dp(2), sv.width() - dp(12));
    }

    void showLine(float x, float y, int w) {
        int[] rl = new int[2];
        root.getLocationOnScreen(rl);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dropLine.getLayoutParams();
        if (lp.width != w) {
            lp.width = Math.max(w, dp(20));
            dropLine.setLayoutParams(lp);
        }
        dropLine.setTranslationX(x - rl[0] - root.getPaddingLeft());
        dropLine.setTranslationY(y - rl[1] - root.getPaddingTop());
        dropLine.setVisibility(View.VISIBLE);
    }

    void setTrayHot(boolean hot) {
        if (trayView != null) trayView.setBackground(hot ? trayHot : trayNormal);
    }

    void applyDrop(Object state) {
        if (overTray) {                                   // dropped on the tray = delete
            if (state instanceof Block) {
                List<Block> p = findParent(program, (Block) state);
                if (p != null) p.remove(state);
                results = null;
                renderWorkspace();
            }
            return;
        }
        if (targetList == null) return;
        int idx = targetIndex;
        if (state instanceof Block) {
            Block b = (Block) state;
            List<Block> from = findParent(program, b);
            if (from != null) {
                int oi = from.indexOf(b);
                if (from == targetList && oi < idx) idx--;
                from.remove(oi);
            }
            targetList.add(Math.min(idx, targetList.size()), b);
        } else {
            targetList.add(Math.min(idx, targetList.size()), newBlock((Integer) state));
        }
        results = null;
        renderWorkspace();
    }

    static List<List<Block>> subLists(Block b) {
        return Arrays.asList(b.thenBody, b.elseBody, b.body);
    }

    /** True if `list` is inside block b (so we can't drop a block into itself). */
    static boolean containsList(Block b, List<Block> list) {
        for (List<Block> sub : subLists(b)) {
            if (sub == list) return true;
            for (Block c : sub) if (containsList(c, list)) return true;
        }
        return false;
    }

    static List<Block> findParent(List<Block> list, Block target) {
        if (list.contains(target)) return list;
        for (Block b : list) {
            for (List<Block> sub : subLists(b)) {
                List<Block> r = findParent(sub, target);
                if (r != null) return r;
            }
        }
        return null;
    }

    Rect rectOf(View v) {
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        return new Rect(l[0], l[1], l[0] + v.getWidth(), l[1] + v.getHeight());
    }

    /* ---------- Variable names used in the program (for dropdowns) ---------- */

    List<String> names() {
        Set<String> s = new LinkedHashSet<>();
        collect(program, s);
        return new ArrayList<>(s);
    }

    void collect(List<Block> list, Set<String> out) {
        for (Block b : list) {
            if ((b.kind == ASK || b.kind == SET || b.kind == FOR) && !b.name.trim().isEmpty()) {
                out.add(b.name.trim());
            }
            for (List<Block> sub : subLists(b)) collect(sub, out);
        }
    }

    /* ============================================================
       6. RUNNING THE PROGRAM
       ============================================================ */

    void toggleRun() {
        if (running) {
            stopRun();
            outputText += "(stopped)\n";
            updateRunButton();
            renderWorkspace();
            renderPanel();
        } else {
            startRun();
        }
    }

    void stopRun() {
        runId++;
        if (runThread != null) runThread.interrupt();
        runThread = null;
        if (activeDialog != null) {
            try { activeDialog.dismiss(); } catch (Exception ignored) { }
            activeDialog = null;
        }
        running = false;
    }

    void resetProgram() {
        stopRun();
        program.clear();
        outputText = "";
        errorText = null;
        results = null;
        updateRunButton();
        renderWorkspace();
        renderPanel();
    }

    void startRun() {
        final Level lv = levels.get(current);
        final int myRun = ++runId;
        running = true;
        outputText = "";
        errorText = null;
        results = null;
        updateRunButton();
        renderWorkspace();
        renderPanel();

        final List<Block> prog = program;
        runThread = new Thread(() -> {
            String err = null;
            Runner r = new Runner(new Runner.IO() {
                @Override
                public String read(String name) {
                    final BlockingQueue<String> q = new ArrayBlockingQueue<>(1);
                    runOnUiThread(() -> {
                        if (myRun == runId) showInputDialog(name, q);
                    });
                    try { return q.take(); } catch (InterruptedException e) { throw new Stop(); }
                }

                @Override
                public void print(String line) {
                    if (myRun != runId) throw new Stop();
                    runOnUiThread(() -> {
                        if (myRun != runId) return;
                        outputText += line + "\n";
                        renderPanel();
                    });
                    try { Thread.sleep(140); } catch (InterruptedException e) { throw new Stop(); }
                }
            });
            try {
                r.run(prog);
            } catch (Stop s) {
                return;
            } catch (ProgramError pe) {
                err = pe.getMessage();
            } catch (RuntimeException re) {
                err = "Something went wrong.";
            }
            if (myRun != runId) return;

            // after the real run, silently check the program with other inputs
            final List<Result> res = (err == null) ? runTests(lv, prog) : null;
            final String fe = err;
            runOnUiThread(() -> {
                if (myRun != runId) return;
                running = false;
                runThread = null;
                errorText = fe;
                results = res;
                if (res != null && allPassed(res)) done[current] = true;
                updateRunButton();
                renderWorkspace();
                renderPanel();
            });
        });
        runThread.start();
    }

    void showInputDialog(String name, final BlockingQueue<String> q) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setHint("type here");
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(et);
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("📥 Input needed")
                .setMessage("Type a value for \"" + name + "\"")
                .setView(wrap)
                .setCancelable(false)
                .setPositiveButton("OK", (dlg, w) -> q.offer(et.getText().toString()))
                .create();
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        activeDialog = d;
        d.show();
        et.requestFocus();
    }

    void updateRunButton() {
        if (runBtn == null) return;
        runBtn.setText(running ? "■ Stop" : "▶ Run");
        runBtn.setBackground(block3d(running ? 0xFFE74C3C : 0xFF2ECC71, dp(12)));
        runBtn.setPadding(dp(14), dp(8), dp(14), dp(11));
    }

    /* ---------- Output + results panel ---------- */

    void renderPanel() {
        if (panelCol == null || current < 0) return;
        panelCol.removeAllViews();
        boolean show = running || !outputText.isEmpty() || errorText != null || results != null;
        panelScroll.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;

        panelCol.addView(text(running ? "OUTPUT  (running…)" : "OUTPUT", 12, 0xFF8EF0B2, true));
        TextView out = new TextView(this);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextSize(14);
        out.setTextColor(0xFFB8FFD0);
        out.setText(outputText.isEmpty() ? "(nothing printed yet)" : outputText.trim());
        panelCol.addView(out);

        if (errorText != null) {
            add(panelCol, text("⚠ " + errorText, 14, 0xFFFF8A80, true));
        }
        if (results != null) {
            Level lv = levels.get(current);
            int pass = 0;
            for (Result r : results) if (r.passed()) pass++;
            if (pass == results.size()) {
                add(panelCol, text("🎉 Level complete!  (" + pass + "/" + results.size() + " checks passed)",
                        18, 0xFFFFD54F, true));
                LinearLayout btns = new LinearLayout(this);
                btns.setOrientation(LinearLayout.HORIZONTAL);
                if (current + 1 < levels.size()) {
                    TextView next = pill("Next level ▶", 0xFF2ECC71, Color.WHITE, 14);
                    next.setOnClickListener(v -> showGame(current + 1));
                    btns.addView(next);
                }
                TextView menu = pill("Menu", 0xFF5A557F, Color.WHITE, 14);
                menu.setOnClickListener(v -> showMenu());
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
                mlp.leftMargin = dp(8);
                btns.addView(menu, mlp);
                add(panelCol, btns);
            } else {
                add(panelCol, text(pass + "/" + results.size() + " checks passed", 14, 0xFFFFD54F, true));
                int shown = 0;
                for (Result r : results) {
                    if (r.passed() || shown >= 3) continue;
                    shown++;
                    String inp = r.inputs.isEmpty() ? "(none)" : join(r.inputs);
                    String got = r.actual.isEmpty() ? "(nothing)" : join(r.actual);
                    String line = "❌ Input " + inp + " → got: " + got + "   expected: " + join(r.expected)
                            + (r.error != null ? "\n    " + r.error : "");
                    add(panelCol, text(line, 13, Color.WHITE, false));
                }
                add(panelCol, text("💡 " + lv.hint, 13, 0xFF9CC9FF, false));
            }
        }
        panelScroll.post(() -> panelScroll.fullScroll(View.FOCUS_DOWN));
    }

    static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    /* ============================================================
       7. UI HELPERS
       ============================================================ */

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(bold ? TITLE_FONT : BODY_FONT);
        return t;
    }

    TextView label(String s) {
        TextView t = text(s, 14, Color.WHITE, true);
        t.setPadding(dp(2), dp(8), dp(2), dp(8));
        return t;
    }

    TextView pill(String s, int fill, int textColor, float sp) {
        TextView t = text(s, sp, textColor, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(block3d(fill, dp(12)));
        t.setPadding(dp(14), dp(8), dp(14), dp(11));
        t.setClickable(true);
        return t;
    }

    /** Typed text box inside a block. Updates the block without redrawing the screen. */
    EditText field(String value, String hint, final Consumer<String> onChange) {
        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(value);
        et.setHint(hint);
        et.setTextSize(14);
        et.setTypeface(BODY_FONT);
        et.setTextColor(0xFF222222);
        et.setHintTextColor(0x66000000);
        et.setBackground(rounded(Color.WHITE, dp(8)));
        et.setPadding(dp(8), dp(6), dp(8), dp(6));
        et.setMinWidth(dp(56));
        et.setEnabled(!running);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { onChange.accept(s.toString()); }
        });
        return et;
    }

    /** Dropdown pill. Options are read when tapped, so they always reflect the latest names. */
    TextView picker(String current, String placeholder, final Supplier<List<String>> options,
                    final Consumer<String> onPick) {
        final TextView t = text((current.isEmpty() ? placeholder : current) + " ▾", 14, 0xFF222222, true);
        t.setBackground(rounded(Color.WHITE, dp(8)));
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setEnabled(!running);
        t.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, t);
            List<String> opts = options.get();
            if (opts.isEmpty()) {
                pm.getMenu().add("(add an Ask or Set block first)").setEnabled(false);
            }
            for (String o : opts) pm.getMenu().add(o);
            pm.setOnMenuItemClickListener(item -> {
                String s = item.getTitle().toString();
                onPick.accept(s);
                t.setText(s + " ▾");
                return true;
            });
            pm.show();
        });
        return t;
    }

    void add(ViewGroup parent, View child) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        parent.addView(child, lp);
    }

    GradientDrawable rounded(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }

    static int darken(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.72f;
        return Color.HSVToColor(hsv);
    }

    /** Lego-style block: main colour with a darker strip along the bottom. */
    Drawable block3d(int color, int r) {
        LayerDrawable ld = new LayerDrawable(new Drawable[]{rounded(darken(color), r), rounded(color, r)});
        ld.setLayerInset(1, 0, 0, 0, dp(3));
        return ld;
    }

    /** Lays children out left-to-right and wraps onto a new line when the row is full. */
    static class FlowLayout extends ViewGroup {
        final int gap;

        FlowLayout(Context c, int gap) {
            super(c);
            this.gap = gap;
        }

        @Override
        protected void onMeasure(int w, int h) {
            int maxW = MeasureSpec.getSize(w);
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                v.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int cw = v.getMeasuredWidth(), ch = v.getMeasuredHeight();
                if (x + cw > maxW && x > 0) {
                    x = 0;
                    y += rowH + gap;
                    rowH = 0;
                }
                x += cw + gap;
                rowH = Math.max(rowH, ch);
            }
            setMeasuredDimension(maxW, y + rowH);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxW = r - l;
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                int cw = v.getMeasuredWidth(), ch = v.getMeasuredHeight();
                if (x + cw > maxW && x > 0) {
                    x = 0;
                    y += rowH + gap;
                    rowH = 0;
                }
                v.layout(x, y, x + cw, y + ch);
                x += cw + gap;
                rowH = Math.max(rowH, ch);
            }
        }
    }
}