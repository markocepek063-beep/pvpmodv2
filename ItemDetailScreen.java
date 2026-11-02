package dev.pvpmod.gui;

import dev.pvpmod.config.PvpConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Per-item screen: colour picker (palette + shade square), live preview, size slider. */
public class ItemDetailScreen extends PvpScreen {
    private static final float[] HUES = {0f, 28f, 52f, 120f, 170f, 215f, 265f, 320f};
    private static final float[][] ROWS = {{0.35f, 1f}, {0.65f, 1f}, {1f, 1f}, {1f, 0.7f}, {1f, 0.4f}};
    private static final int PAL_COLS = HUES.length + 1, PAL_ROWS = ROWS.length, PAL = 10;
    private static final int SQ_COLS = 12, SQ_ROWS = 7, SQ = 10;
    private static final int BOX = 96;

    private record Swatch(int x, int y, int kind, int col, int row) {}

    private final String id;
    private final ItemStack stack;
    private float hue = 0f;
    private final List<Swatch> swatches = new ArrayList<>();
    private int px, py, sx, sy, bx, by;

    public ItemDetailScreen(Screen parent, String itemId) {
        super(Registries.ITEM.get(Identifier.of(itemId)).getName().getString(), parent);
        this.id = itemId;
        this.stack = new ItemStack(Registries.ITEM.get(Identifier.of(itemId)));
    }

    // ---- colour maths ----
    private static int hsv(float h, float s, float v) {
        float c = v * s;
        float hh = ((h % 360f) + 360f) % 360f / 60f;
        float x = c * (1f - Math.abs(hh % 2f - 1f));
        float m = v - c;
        float r = 0, g = 0, b = 0;
        if (hh < 1) { r = c; g = x; }
        else if (hh < 2) { r = x; g = c; }
        else if (hh < 3) { g = c; b = x; }
        else if (hh < 4) { g = x; b = c; }
        else if (hh < 5) { r = x; b = c; }
        else { r = c; b = x; }
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    private static float hueOf(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        if (d == 0) return 0f;
        float h;
        if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h *= 60f;
        return h < 0 ? h + 360f : h;
    }

    private int colorOf(Swatch s) {
        if (s.kind() == 0) {
            if (s.col() == 0) {
                float v = 1f - s.row() / (float) (PAL_ROWS - 1);
                return hsv(0f, 0f, v);
            }
            float[] sv = ROWS[s.row()];
            return hsv(HUES[s.col() - 1], sv[0], sv[1]);
        }
        float sat = s.col() / (float) (SQ_COLS - 1);
        float val = 1f - s.row() * 0.85f / (SQ_ROWS - 1);
        return hsv(hue, sat, val);
    }

    private void pick(Swatch s) {
        int rgb = colorOf(s);
        PvpConfig.get().itemColors.put(id, rgb);
        if (s.kind() == 0) {          // palette click: move the shade square to that hue
            hue = hueOf(rgb);
            clearAndInit();
        }
    }

    // ---- layout ----
    @Override
    protected void init() {
        PvpConfig c = PvpConfig.get();
        Integer existing = c.itemColors.get(id);
        if (existing != null && hue == 0f) hue = hueOf(existing);

        swatches.clear();
        int cx = this.width / 2;
        px = cx - 215; py = 52;
        sx = cx - 80;  sy = 52;
        bx = cx + 60;  by = 52;

        for (int col = 0; col < PAL_COLS; col++)
            for (int row = 0; row < PAL_ROWS; row++)
                addSwatch(new Swatch(px + col * (PAL + 1), py + row * (PAL + 1), 0, col, row), PAL);

        for (int col = 0; col < SQ_COLS; col++)
            for (int row = 0; row < SQ_ROWS; row++)
                addSwatch(new Swatch(sx + col * (SQ + 1), sy + row * (SQ + 1), 1, col, row), SQ);

        int sqW = SQ_COLS * (SQ + 1) - 1;
        int y = sy + SQ_ROWS * (SQ + 1) + 6;
        addDrawableChild(new FloatSlider(sx, y, sqW, H, "Hue", 0f, 359f, hue, v -> hue = v));

        int y2 = y + GAP + 8;
        addDrawableChild(new FloatSlider(cx - 100, y2, 200, H, "Size", 0.25f, 2.5f,
            c.itemScales.getOrDefault(id, 1f), v -> c.itemScales.put(id, v)));
        addDrawableChild(button(cx - 100, y2 + GAP, 98, "Reset colour", () -> c.itemColors.remove(id)));
        addDrawableChild(button(cx + 2, y2 + GAP, 98, "Reset size", () -> { c.itemScales.remove(id); clearAndInit(); }));
        addDone();
    }

    private void addSwatch(Swatch s, int size) {
        swatches.add(s);
        addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget
            .builder(Text.empty(), b -> pick(s))
            .dimensions(s.x(), s.y(), size, size).build());
    }

    // ---- drawing ----
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        PvpConfig c = PvpConfig.get();

        for (Swatch s : swatches) {
            int size = s.kind() == 0 ? PAL : SQ;
            ctx.fill(s.x(), s.y(), s.x() + size, s.y() + size, 0xFF000000 | colorOf(s));
        }

        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Colour picker"), px, py - 12, 0xFFAAAAAA);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Preview"), bx, by - 12, 0xFFAAAAAA);

        Integer col = c.itemColors.get(id);
        int backdrop = col == null ? 0xFF202020 : 0xFF000000 | mix(col, 0x202020);
        ctx.fill(bx - 3, by - 3, bx + BOX + 3, by + BOX + 3, 0xFFFFFFFF);
        ctx.fill(bx, by, bx + BOX, by + BOX, backdrop);

        float scale = 2.0f * c.itemScales.getOrDefault(id, 1f);
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(bx + BOX / 2f, by + BOX / 2f);
        m.scale(scale, scale);
        ctx.drawItem(stack, -8, -8);
        m.popMatrix();

        String label = col == null ? "Colour: default" : String.format("Colour: #%06X", col & 0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(label), bx, by + BOX + 8, 0xFFFFFFFF);
    }

    private static int mix(int a, int b) {
        int r = (((a >> 16) & 255) + ((b >> 16) & 255)) / 2;
        int g = (((a >> 8) & 255) + ((b >> 8) & 255)) / 2;
        int bl = ((a & 255) + (b & 255)) / 2;
        return (r << 16) | (g << 8) | bl;
    }
}
