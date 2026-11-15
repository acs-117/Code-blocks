package com.example.codeblocks;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {

    /* ============================================================
       1. BLOCKS (the program the child builds)
       ============================================================ */

    static final String NONE = "(nothing)";
    static final int INPUT = 0, PRINT = 1, IF = 2, FOR = 3;
    static final String[] KIND_LABELS = {"📥 Get input", "💬 Print", "❓ If / Otherwise", "🔁 For loop"};

    static class Block {
        int kind;
        String variable = "";
        String text = "";
        int divisor = 2, remainder = 0, from = 1, to = 5;
        List<Block> thenBody = new ArrayList<>();
        List<Block> elseBody = new ArrayList<>();
        List<Block> body = new ArrayList<>();

        Block(int kind) { this.kind = kind; }
    }

    static Block newBlock(int kind, Level lv) {
        Block b = new Block(kind);
        b.variable = (kind == PRINT) ? NONE : lv.vars[0];
        b.text = lv.texts[0];
        return b;
    }

    /* ============================================================
       2. LEVELS (data only - add more here later)
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
        String[] vars, texts;   // variable names / Print text choices
        int[] kinds;            // blocks available in the palette
        List<TestCase> tests;

        Level(String title, String task, String concept, String hint,
              String[] vars, String[] texts, int[] kinds, List<TestCase> tests) {
            this.title = title; this.task = task; this.concept = concept; this.hint = hint;
            this.vars = vars; this.texts = texts; this.kinds = kinds; this.tests = tests;
        }
    }

    static List<Level> buildLevels() {
        List<Level> list = new ArrayList<>();

        list.add(new Level(
                "Say Hello",
                "Ask for a name, then print Hello and the name.",
                "INPUT gets a value. A VARIABLE is a box that remembers it. PRINT shows it.",
                "Use 'Get input' first so the name is stored. Then Print \"Hello \" + name.",
                new String[]{"name"},
                new String[]{"Hello ", "Bye "},
                new int[]{INPUT, PRINT},
                Arrays.asList(
                        new TestCase(Arrays.asList("Asha"), Arrays.asList("Hello Asha")),
                        new TestCase(Arrays.asList("Ravi"), Arrays.asList("Hello Ravi")),
                        new TestCase(Arrays.asList("Sam"), Arrays.asList("Hello Sam")))));

        list.add(new Level(
                "Odd or Even",
                "Get a number. Print Even if it is even, otherwise print Odd.",
                "IF / OTHERWISE lets a program choose. The remainder after dividing by 2 tells odd from even.",
                "Even numbers leave remainder 0 when divided by 2. Put Print \"Even\" in 'then' and " +
                        "Print \"Odd\" in 'otherwise'. Tap a box to highlight it before adding blocks.",
                new String[]{"n"},
                new String[]{"Even", "Odd"},
                new int[]{INPUT, PRINT, IF},
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
                "A FOR LOOP repeats blocks. The variable i changes each time around.",
                "Add a For loop from 1 to 10. Highlight its inner box, then add Print and choose the variable i.",
                new String[]{"i"},
                new String[]{""},
                new int[]{PRINT, FOR},
                Arrays.asList(new TestCase(new ArrayList<String>(), oneToTen))));

        return list;
    }

    /* ============================================================
       3. INTERPRETER (runs the blocks and collects printed output)
       ============================================================ */

    static class Runner {
        Map<String, String> vars = new HashMap<>();
        List<String> inputs;
        List<String> out = new ArrayList<>();
        int inputIndex = 0, steps = 0;

        Runner(List<String> inputs) { this.inputs = inputs; }

        void run(List<Block> list) {
            for (Block b : list) {
                if (++steps > 2000) {
                    throw new IllegalStateException("Too many steps - is there a loop that never ends?");
                }
                switch (b.kind) {
                    case INPUT: {
                        vars.put(b.variable, inputIndex < inputs.size() ? inputs.get(inputIndex) : "");
                        inputIndex++;
                        break;
                    }
                    case PRINT: {
                        String v = b.variable.equals(NONE) ? "" : vars.getOrDefault(b.variable, "");
                        out.add(b.text + v);
                        break;
                    }
                    case IF: {
                        int n;
                        try { n = Integer.parseInt(vars.getOrDefault(b.variable, "")); }
                        catch (NumberFormatException e) { n = 0; }
                        if (n % b.divisor == b.remainder) run(b.thenBody); else run(b.elseBody);
                        break;
                    }
                    case FOR: {
                        for (int k = b.from; k <= b.to; k++) {
                            vars.put(b.variable, String.valueOf(k));
                            run(b.body);
                        }
                        break;
                    }
                }
            }
        }
    }

    static class Result {
        List<String> inputs, expected, actual;
        String error;
        Result(List<String> i, List<String> e, List<String> a, String err) {
            inputs = i; expected = e; actual = a; error = err;
        }
        boolean passed() { return error == null && expected.equals(actual); }
    }

    static List<Result> runTests(Level lv, List<Block> program) {
        List<Result> results = new ArrayList<>();
        for (TestCase t : lv.tests) {
            Runner r = new Runner(t.inputs);
            try {
                r.run(program);
                results.add(new Result(t.inputs, t.expected, r.out, null));
            } catch (Exception e) {
                results.add(new Result(t.inputs, t.expected, r.out, e.getMessage()));
            }
        }
        return results;
    }

    /* ============================================================
       4. UI (all built in code, no XML needed)
       ============================================================ */

    List<Level> levels = buildLevels();
    boolean[] done = new boolean[levels.size()];
    int current = -1;
    List<Block> program;
    List<Block> selected;
    List<Result> results;

    FrameLayout root;
    LinearLayout gameCol;
    OnBackPressedCallback backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setContentView(root);

        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() { showMenu(); }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);

        showMenu();
    }

    /* ---------- Level menu ---------- */

    void showMenu() {
        current = -1;
        backCallback.setEnabled(false);
        root.removeAllViews();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.addView(col);

        add(col, text("🧩 Block Coder", 32, Color.BLACK, true));
        add(col, text("Pick a level", 18, Color.DKGRAY, false));

        for (int i = 0; i < levels.size(); i++) {
            final int idx = i;
            boolean unlocked = (i == 0) || done[i - 1];
            String icon = done[i] ? "✅" : (unlocked ? "▶" : "🔒");
            Button b = button(icon + "  Level " + (i + 1) + ": " + levels.get(i).title, v -> showGame(idx));
            b.setEnabled(unlocked);
            b.setTextSize(18);
            add(col, b);
        }
    }

    /* ---------- Game screen ---------- */

    void showGame(int idx) {
        current = idx;
        program = new ArrayList<>();
        selected = program;
        results = null;
        backCallback.setEnabled(true);

        root.removeAllViews();
        ScrollView sv = new ScrollView(this);
        gameCol = new LinearLayout(this);
        gameCol.setOrientation(LinearLayout.VERTICAL);
        gameCol.setPadding(dp(12), dp(12), dp(12), dp(60));
        sv.addView(gameCol);
        root.addView(sv);

        renderGame();
    }

    void renderGame() {
        final Level lv = levels.get(current);
        gameCol.removeAllViews();

        add(gameCol, button("← Menu", v -> showMenu()));
        add(gameCol, text(lv.title, 26, Color.BLACK, true));
        add(gameCol, text(lv.task, 16, Color.BLACK, false));
        add(gameCol, text("💡 " + lv.concept, 13, Color.GRAY, false));

        add(gameCol, text("YOUR PROGRAM", 14, Color.BLACK, true));
        add(gameCol, listView(program, lv));

        add(gameCol, text("BLOCKS  (tap to add into the orange box)", 14, Color.BLACK, true));
        FlowLayout palette = new FlowLayout(this);
        for (final int kind : lv.kinds) {
            palette.addView(button(KIND_LABELS[kind], v -> {
                selected.add(newBlock(kind, lv));
                results = null;
                renderGame();
            }));
        }
        add(gameCol, palette);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button run = button("▶ RUN", v -> {
            results = runTests(lv, program);
            boolean all = true;
            for (Result r : results) if (!r.passed()) all = false;
            if (all) done[current] = true;
            renderGame();
        });
        run.setBackgroundColor(Color.parseColor("#2E7D32"));
        run.setTextColor(Color.WHITE);
        actions.addView(run);
        actions.addView(button("↺ RESET", v -> {
            program.clear();
            selected = program;
            results = null;
            renderGame();
        }));
        add(gameCol, actions);

        if (results != null) {
            add(gameCol, text("TEST RESULTS", 14, Color.BLACK, true));
            boolean all = true;
            for (Result r : results) {
                String inp = r.inputs.isEmpty() ? "(none)" : join(r.inputs, ", ");
                String out = r.actual.isEmpty() ? "(nothing printed)" : join(r.actual, " ");
                String line;
                if (r.passed()) {
                    line = "✅ Input: " + inp + " → " + out;
                } else {
                    all = false;
                    line = "❌ Input: " + inp + " → " + out + "\n    expected: " + join(r.expected, " ")
                            + (r.error != null ? "\n    " + r.error : "");
                }
                add(gameCol, text(line, 14, Color.BLACK, false));
            }
            if (all) {
                add(gameCol, text("🎉 You did it!", 22, Color.parseColor("#2E7D32"), true));
                add(gameCol, button("Back to menu", v -> showMenu()));
            } else {
                add(gameCol, text("💡 Hint: " + lv.hint, 14, Color.parseColor("#1565C0"), false));
            }
        }
    }

    /* ---------- A box that holds blocks (a "slot") ---------- */

    View listView(final List<Block> list, Level lv) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6), dp(6), dp(6), dp(6));
        boolean isSel = (list == selected);
        box.setBackground(bg(Color.WHITE, isSel ? Color.parseColor("#FF9800") : Color.LTGRAY, dp(2)));
        box.setOnClickListener(v -> {
            selected = list;
            renderGame();
        });

        if (list.isEmpty()) {
            add(box, text("(empty - tap here, then add blocks)", 12, Color.GRAY, false));
        }
        for (Block b : new ArrayList<>(list)) {
            add(box, blockView(b, list, lv));
        }
        return box;
    }

    /* ---------- One block ---------- */

    View blockView(final Block b, final List<Block> parent, final Level lv) {
        int color;
        switch (b.kind) {
            case INPUT: color = Color.parseColor("#43A047"); break;
            case PRINT: color = Color.parseColor("#1E88E5"); break;
            case IF:    color = Color.parseColor("#F57C00"); break;
            default:    color = Color.parseColor("#8E24AA"); break;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(bg(color, 0, 0));

        // header row: [controls ...............] [✕]
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        FlowLayout controls = new FlowLayout(this);
        header.addView(controls, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button remove = new Button(this);
        remove.setText("✕");
        remove.setTextColor(Color.WHITE);
        remove.setBackgroundColor(Color.TRANSPARENT);
        remove.setMinWidth(0);
        remove.setMinimumWidth(0);
        remove.setOnClickListener(v -> {
            parent.remove(b);
            selected = program;
            results = null;
            renderGame();
        });
        header.addView(remove);
        card.addView(header);

        switch (b.kind) {
            case INPUT:
                controls.addView(label("📥 get input → store in"));
                controls.addView(picker(b.variable, lv.vars, i -> b.variable = lv.vars[i]));
                break;

            case PRINT: {
                controls.addView(label("💬 print"));
                String[] textLabels = new String[lv.texts.length];
                for (int i = 0; i < textLabels.length; i++) textLabels[i] = fmt(lv.texts[i]);
                controls.addView(picker(fmt(b.text), textLabels, i -> b.text = lv.texts[i]));
                controls.addView(label("+"));
                final String[] varOptions = new String[lv.vars.length + 1];
                varOptions[0] = NONE;
                System.arraycopy(lv.vars, 0, varOptions, 1, lv.vars.length);
                controls.addView(picker(b.variable, varOptions, i -> b.variable = varOptions[i]));
                break;
            }

            case IF:
                controls.addView(label("❓ if"));
                controls.addView(picker(b.variable, lv.vars, i -> b.variable = lv.vars[i]));
                controls.addView(label("÷"));
                controls.addView(picker("" + b.divisor, range(1, 10), i -> b.divisor = i + 1));
                controls.addView(label("leaves remainder"));
                controls.addView(picker("" + b.remainder, range(0, 9), i -> b.remainder = i));
                add(card, label("then:"));
                add(card, listView(b.thenBody, lv));
                add(card, label("otherwise:"));
                add(card, listView(b.elseBody, lv));
                break;

            case FOR:
                controls.addView(label("🔁 for"));
                controls.addView(picker(b.variable, lv.vars, i -> b.variable = lv.vars[i]));
                controls.addView(label("from"));
                controls.addView(picker("" + b.from, range(1, 10), i -> b.from = i + 1));
                controls.addView(label("to"));
                controls.addView(picker("" + b.to, range(1, 10), i -> b.to = i + 1));
                add(card, label("repeat:"));
                add(card, listView(b.body, lv));
                break;
        }
        return card;
    }

    /* ---------- Small UI helpers ---------- */

    /** A dropdown button. onPick receives the index of the chosen option. */
    Button picker(String current, final String[] options, final Consumer<Integer> onPick) {
        final Button btn = new Button(this);
        btn.setText(current + " ▾");
        btn.setAllCaps(false);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, btn);
            for (int i = 0; i < options.length; i++) pm.getMenu().add(0, i, i, options[i]);
            pm.setOnMenuItemClickListener(item -> {
                onPick.accept(item.getItemId());
                results = null;
                renderGame();
                return true;
            });
            pm.show();
        });
        return btn;
    }

    TextView label(String s) {
        TextView t = text(s, 14, Color.WHITE, true);
        t.setPadding(dp(6), dp(14), dp(6), dp(6));
        return t;
    }

    TextView text(String s, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    /** Adds a view with a small top margin when the parent is a vertical LinearLayout. */
    void add(ViewGroup parent, View child) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        parent.addView(child, lp);
    }

    GradientDrawable bg(int fill, int stroke, int strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(8));
        if (strokeWidth > 0) g.setStroke(strokeWidth, stroke);
        return g;
    }

    static String[] range(int a, int b) {
        String[] r = new String[b - a + 1];
        for (int i = 0; i < r.length; i++) r[i] = String.valueOf(a + i);
        return r;
    }

    static String fmt(String s) { return s.isEmpty() ? "(empty)" : "\"" + s + "\""; }

    static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    /** Lays children out left-to-right and wraps to a new line when full. */
    static class FlowLayout extends ViewGroup {
        FlowLayout(Context c) { super(c); }

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
                if (x + cw > maxW && x > 0) { x = 0; y += rowH; rowH = 0; }
                x += cw;
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
                if (x + cw > maxW && x > 0) { x = 0; y += rowH; rowH = 0; }
                v.layout(x, y, x + cw, y + ch);
                x += cw;
                rowH = Math.max(rowH, ch);
            }
        }
    }
}