package com.portaltiers.tagger.gui;

import com.portaltiers.tagger.PortalTierManager;
import com.portaltiers.tagger.config.PortalConfig;
import com.portaltiers.tagger.model.GameMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;

/**
 * A self-contained vanilla-widget config screen (no Cloth Config dependency).
 */
public class ConfigScreen extends Screen {
    private final Screen parent;
    private final PortalConfig cfg = PortalConfig.get();

    public ConfigScreen(Screen parent) {
        super(Text.literal("Pojav Tier Tagger Config"));   // changed title
        this.parent = parent;
    }

    @Override
    protected void init() {
        int colW = 150;
        int gap = 4;
        int rowH = 20;
        int leftX = this.width / 2 - colW - gap / 2;
        int rightX = this.width / 2 + gap / 2;
        int y = 40;

        // Row 1: enabled / icons
        addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.enabled)
                .build(leftX, y, colW, rowH, Text.translatable("portaltiertagger.config.enabled"),
                        (b, v) -> cfg.enabled = v));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.showIcons)
                .build(rightX, y, colW, rowH, Text.translatable("portaltiertagger.config.showIcons"),
                        (b, v) -> cfg.showIcons = v));
        y += rowH + gap;

        // Row 2: nametag / tab
        addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.showInNametag)
                .build(leftX, y, colW, rowH, Text.translatable("portaltiertagger.config.showInNametag"),
                        (b, v) -> cfg.showInNametag = v));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.showInTab)
                .build(rightX, y, colW, rowH, Text.translatable("portaltiertagger.config.showInTab"),
                        (b, v) -> cfg.showInTab = v));
        y += rowH + gap;

        // Row 3: chat / brackets
        addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.showInChat)
                .build(leftX, y, colW, rowH, Text.translatable("portaltiertagger.config.showInChat"),
                        (b, v) -> cfg.showInChat = v));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.useBrackets)
                .build(rightX, y, colW, rowH, Text.translatable("portaltiertagger.config.bracket"),
                        (b, v) -> cfg.useBrackets = v));
        y += rowH + gap;

        // Row 4: gamemode (cycling)
        addDrawableChild(CyclingButtonWidget.<GameMode>builder(g -> Text.literal(g.displayName()))
                .values(GameMode.values())
                .initially(cfg.gamemode)
                .build(leftX, y, colW, rowH, Text.translatable("portaltiertagger.config.gamemode"),
                        (b, v) -> cfg.gamemode = v));
        // highest mode
        addDrawableChild(CyclingButtonWidget.<PortalConfig.HighestMode>builder(m -> Text.literal(label(m)))
                .values(PortalConfig.HighestMode.values())
                .initially(cfg.highestMode)
                .build(rightX, y, colW, rowH, Text.translatable("portaltiertagger.config.highestMode"),
                        (b, v) -> cfg.highestMode = v));
        y += rowH + gap;

        // Row 5: refresh interval (cycling presets)
        Integer[] intervals = {5, 10, 15, 30, 60};
        Integer current = nearest(cfg.refreshIntervalMinutes, intervals);
        addDrawableChild(CyclingButtonWidget.<Integer>builder(i -> Text.literal(i + " min"))
                .values(intervals)
                .initially(current)
                .build(leftX, y, colW, rowH, Text.translatable("portaltiertagger.config.refresh"),
                        (b, v) -> cfg.refreshIntervalMinutes = v));
        // manual refresh button
        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Refresh now (" + PortalTierManager.size() + " loaded)"),
                        b -> PortalTierManager.refreshNow())
                .dimensions(rightX, y, colW, rowH).build());
        y += rowH + gap + 6;

        // Done
        addDrawableChild(ButtonWidget.builder(Text.translatable("portaltiertagger.config.done"), b -> close())
                .dimensions(this.width / 2 - 100, y, 200, rowH).build());
    }

    private static String label(PortalConfig.HighestMode m) {
        return switch (m) {
            case NEVER -> "Highest: Never";
            case IF_NONE -> "Highest: If none";
            case ALWAYS -> "Highest: Always";
        };
    }

    private static Integer nearest(int value, Integer[] options) {
        Integer best = options[0];
        int diff = Integer.MAX_VALUE;
        for (Integer o : options) {
            int d = Math.abs(o - value);
            if (d < diff) {
                diff = d;
                best = o;
            }
        }
        return best;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFF);
    }

    @Override
    public void close() {
        cfg.save();
        PortalTierManager.clearCache();
        PortalTierManager.refreshNow();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
