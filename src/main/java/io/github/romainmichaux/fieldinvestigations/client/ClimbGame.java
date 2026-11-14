package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Climb (canopy investigation): the trunk scrolls down past the climber's hands. Grab each hold on its side as it
// reaches them (dead wood is let go by), both sides at once for a double hold, and hang on to vines while they pass.
// A grab right on the hands is perfect and gives a little momentum; a miss cracks the branch and the climber slides.
final class ClimbGame extends MiniGame {
    static final ResourceLocation TRUNK = Draw.texture("trunk"), FOREST = Draw.texture("forest"), CLIMBER = Draw.texture("climb_climber"),
            CANOPY = Draw.texture("climb_canopy");
    static final ResourceLocation[][] BRANCHES = {{Draw.texture("branch_left"), Draw.texture("branch_right")},
            {Draw.texture("dead_branch_left"), Draw.texture("dead_branch_right")}};
    static final java.util.Set<String> KINDS = java.util.Set.of("stat", "size", "egg", "type1", "type2");
    static final int BAND = 56, TRUNK_W = 48, TRUNK_X = (AREA_W - TRUNK_W) / 2, N = 18, LINE = 74, MARGIN = 9, PERFECT = 2, NEAR = 28;
    static final int SINGLE = 0, DEAD = 1, DOUBLE = 2, VINE = 3;
    static final int[] LEFT = {InputConstants.KEY_LEFT, InputConstants.KEY_A, InputConstants.KEY_Q};
    static final int[] RIGHT = {InputConstants.KEY_RIGHT, InputConstants.KEY_D};
    final int[] side = new int[N], heightPx = new int[N], kind = new int[N], length = new int[N];
    // 0 = upcoming, 1 = passed (grabbed, or dead wood let go), 2 = missed.
    final int[] state = new int[N];
    final ResourceLocation trace;
    final int top;
    double progress;
    int next, faults, flash, calm, letGo, holds, liveHolds, tic, shake, reach, reachSide, gripped, summit = -1, feedbackTicks;
    boolean leftDone, rightDone, onVine, leftWasDown, rightWasDown;
    String feedback;
    int feedbackColour;

    ClimbGame(Random h, String clue) {
        trace = Draw.texture("abseil_" + (KINDS.contains(clue) ? clue : "stat"));
        int y = 0;
        for (int i = 0; i < N; i++) {
            side[i] = h.nextInt(2);
            y += 22 + h.nextInt(24);
            heightPx[i] = y;
            int r = h.nextInt(10);
            kind[i] = i < 2 ? SINGLE : r < 2 ? DEAD : r < 4 ? DOUBLE : r < 5 ? VINE : SINGLE;
            if (kind[i] == VINE) {
                length[i] = 28 + h.nextInt(22);
                y += length[i];
            }
            if (kind[i] != DEAD) liveHolds++;
        }
        top = y + 40;
    }

    double y(int i) {
        return progress - 20 - heightPx[i];
    }

    double speed() {
        return summit >= 0 ? 0 : 1.45 + Math.max(0, progress) / 450;
    }

    static boolean down(int[] keys) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        for (int k : keys) if (InputConstants.isKeyDown(window, k)) return true;
        return false;
    }

    boolean held(int s, double mx) {
        boolean mouse = mouseDown && (s == 0 ? mx < ox + AREA_W / 2 : mx >= ox + AREA_W / 2);
        return mouse || down(s == 0 ? LEFT : RIGHT);
    }

    double cx;

    void say(String text, int colour) {
        feedback = text;
        feedbackColour = colour;
        feedbackTicks = 18;
    }

    void slip() {
        state[next++] = 2;
        flash = 8;
        shake = 10;
        leftDone = rightDone = onVine = false;
        // The branch cracks and the climber slides down a few metres.
        progress -= 26;
        // A short calm after a slip: a second key pressed in the same panic does not count again.
        calm = 10;
        Draw.beep(Sounds.CRACK, 0.9f, 0.8f);
        Draw.beep(Sounds.ROCKSLIDE, 1.6f, 0.3f);
        say(T.f("slipped"), Draw.ALERT);
        if (++faults >= 3) end(false);
    }

    void grabbed(double distance, int s) {
        state[next++] = 1;
        leftDone = rightDone = onVine = false;
        holds++;
        reach = 6;
        reachSide = s;
        Draw.beep(Sounds.BRANCH, 0.8f + holds * 0.04f, 0.9f);
        if (distance <= PERFECT) {
            // A clean grab: a little momentum up the trunk.
            progress += 7;
            say(T.f("perfect"), Draw.YELLOW);
            Draw.note(1.3f + Math.min(8, holds) * 0.05f);
        } else {
            say(T.f("good"), Draw.GREEN);
        }
    }

    // Once every hold is passed, the climb goes on until the branch with the trace reaches the hands.
    void checkDone() {
        if (!finished && next >= N && summit < 0 && progress - 20 - top >= LINE - 6) {
            summit = 0;
            Draw.beep(Sounds.SUCCESS, 1.2f, 0.4f);
        }
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (shake > 0) shake--;
        if (calm > 0) calm--;
        if (reach > 0) reach--;
        if (feedbackTicks > 0) feedbackTicks--;
        leftWasDown = down(LEFT);
        rightWasDown = down(RIGHT);
        if (summit >= 0) {
            // The canopy opens on the trace; the climber takes it.
            if (++summit == 24) {
                reach = 12;
                reachSide = 1;
                Draw.beep(Sounds.CHIME, 1.6f, 0.6f);
            }
            if (summit >= 44) {
                perfect = faults == 0;
                end(true);
            }
            return;
        }
        progress += speed();
        if (next >= N) {
            checkDone();
            return;
        }
        double yy = y(next);
        switch (kind[next]) {
            case DEAD -> {
                if (yy > LINE + MARGIN) state[next++] = 1; // dead wood let go by: the right move
            }
            case VINE -> {
                if (onVine) {
                    // Hang on until the top of the vine nears the hands. Letting go gives a moment of grace, and letting go
                    // in the last stretch already counts as done.
                    double left = length[next] - (yy - LINE);
                    if (left <= 12) {
                        grabbed(99, side[next]);
                        calm = 6;
                    } else if (!held(side[next], cx)) {
                        if (++letGo > 6) slip();
                    } else {
                        letGo = 0;
                        if (tic % 6 == 0) Draw.beep(Sounds.ROPE, 1.2f, 0.4f);
                    }
                } else if (yy > LINE + MARGIN) {
                    slip();
                }
            }
            default -> {
                if (yy > LINE + MARGIN) slip();
            }
        }
        checkDone();
    }

    void press(int s) {
        if (next >= N || summit >= 0 || finished || calm > 0) return;
        double distance = Math.abs(y(next) - LINE);
        // A press with no hold anywhere near the hands is just a twitch: ignored.
        if (distance > NEAR && !(kind[next] == VINE && onVine)) return;
        boolean inRange = distance <= MARGIN;
        switch (kind[next]) {
            case DEAD -> slip(); // dead wood snaps under the hand
            case DOUBLE -> {
                if (!inRange) {
                    slip();
                    return;
                }
                if (s == 0) leftDone = true;
                else rightDone = true;
                reach = 6;
                reachSide = s;
                if (leftDone && rightDone) grabbed(distance, s);
            }
            case VINE -> {
                if (onVine) return;
                if (inRange && s == side[next]) {
                    onVine = true;
                    letGo = 0;
                    reach = 6;
                    reachSide = s;
                    Draw.beep(Sounds.ROPE, 0.9f, 0.7f);
                } else {
                    slip();
                }
            }
            default -> {
                if (inRange && s == side[next]) grabbed(distance, s);
                else slip(); // too early, too late, or the wrong side
            }
        }
        checkDone();
    }

    @Override
    void click(double mx, double my) {
        if (!Draw.in(mx, my, ox, oy, AREA_W, AREA_H)) return;
        press(mx < ox + AREA_W / 2 ? 0 : 1);
    }

    // Held keys repeat: only a fresh press (key up at the previous tick) counts.
    @Override
    boolean keyPressed(int key) {
        for (int k : LEFT) {
            if (k == key) {
                if (!leftWasDown) press(0);
                leftWasDown = true;
                return true;
            }
        }
        for (int k : RIGHT) {
            if (k == key) {
                if (!rightWasDown) press(1);
                rightWasDown = true;
                return true;
            }
        }
        return false;
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        cx = mx;
        int ax = ox, ay = oy;
        double offset = finished ? 0 : pt * speed(), climbed = progress + offset;
        int jolt = shake > 0 ? (shake % 2 == 0 ? 2 : -2) : 0;
        g.enableScissor(ax + 1, ay + 1, ax + AREA_W - 1, ay + AREA_H - 1);
        // The forest: the painting in the middle, its edges repeated on both sides, a little darker towards the edges.
        int fx = ax + (AREA_W - 160) / 2;
        Draw.image(g, FOREST, fx, ay + 3, 160, 112);
        Draw.piece(g, FOREST, ax, ay + 3, 160 - (fx - ax), 0, fx - ax, 112, 160, 112);
        Draw.piece(g, FOREST, fx + 160, ay + 3, 0, 0, ax + AREA_W - fx - 160, 112, 160, 112);
        g.fill(ax, ay, ax + 40, ay + AREA_H, 0x50000000);
        g.fill(ax + AREA_W - 40, ay, ax + AREA_W, ay + AREA_H, 0x50000000);
        g.fill(ax, ay, ax + AREA_W, ay + 3, 0xFF2C4A2A);
        g.fill(ax, ay + 115, ax + AREA_W, ay + AREA_H, 0xFF1E3420);

        g.pose().pushPose();
        g.pose().translate(0, jolt, 0);
        // Holds: drawn before the trunk, which covers their base so they grow out of it.
        for (int i = 0; i < N; i++) {
            double y = y(i) + offset;
            if (state[i] == 1 && kind[i] != VINE || state[i] == 2) continue;
            if (y < -60 || y > AREA_H + 20) continue;
            int py = ay + (int) y;
            boolean current = i == next && Math.abs(y(i) - LINE) <= MARGIN && kind[i] != DEAD;
            if (kind[i] == VINE) {
                vine(g, i, py, current || i == next && onVine);
                continue;
            }
            for (int s = 0; s <= 1; s++) {
                if (kind[i] != DOUBLE && s != side[i]) continue;
                int x = ax + (s == 0 ? TRUNK_X - 26 : TRUNK_X + TRUNK_W - 6);
                boolean done = kind[i] == DOUBLE && i == next && (s == 0 ? leftDone : rightDone);
                if (current && !done) g.fill(x - 2, py - 10, x + 34, py + 10, 0x6040E070);
                if (done) g.fill(x - 2, py - 10, x + 34, py + 10, 0x60F2C93A);
                Draw.image(g, BRANCHES[kind[i] == DEAD ? 1 : 0][s], x, py - 16, 32, 32);
            }
            if (kind[i] == DOUBLE) Draw.centre(g, "×2", ax + TRUNK_X + TRUNK_W / 2, py - 4, 0xFFF2C93A);
        }
        // The top of the tree is part of the trunk: the trace branch and the bush above it scroll with the holds.
        int traceY = ay + (int) Math.round(climbed - 20 - top), bushBottom = traceY - 2, bushTop = bushBottom - 74;
        int scroll = Math.floorMod((int) climbed, BAND), trunkTop = Math.max(ay, bushTop + 30);
        g.enableScissor(ax + 1, trunkTop, ax + AREA_W - 1, ay + AREA_H - 1);
        g.fill(ax + TRUNK_X - 2, ay, ax + TRUNK_X + TRUNK_W + 2, ay + AREA_H, 0x60000000);
        for (int k = -1; k <= 2; k++) Draw.image(g, TRUNK, ax + TRUNK_X, ay + scroll + k * BAND, TRUNK_W, BAND);
        g.disableScissor();
        g.pose().popPose();

        if (bushBottom > ay - 10) {
            int tx = ax + TRUNK_X + TRUNK_W + 6, ty = traceY;
            Draw.image(g, BRANCHES[0][1], tx - 12, ty - 8, 32, 32);
            g.pose().pushPose();
            g.pose().translate(tx + 6, ty - 2, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            if (summit < 24 || summit % 10 < 7) Draw.image(g, trace, 0, 0, 32, 32);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(ax + AREA_W / 2f - 114 * 0.65f, bushTop, 0);
            g.pose().scale(1.3f, 1.3f, 1f);
            Draw.image(g, CANOPY, 0, 0, 114, 57);
            g.pose().popPose();
        }

        // The climber, hands on the line, leaning towards the hand that reaches out.
        float lean = reach > 0 ? (reachSide == 0 ? -12f : 12f) * reach / 6f : (float) Math.sin(tic * 0.2) * 2f;
        int slide = shake > 0 ? -shake : 0;
        g.pose().pushPose();
        g.pose().translate(ax + AREA_W / 2f, ay + LINE + 2 + slide + jolt, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(lean));
        g.pose().scale(0.8f, 0.8f, 1f);
        if (shake > 0 && shake % 4 < 2) g.setColor(1f, 0.55f, 0.5f, 1f);
        Draw.image(g, CLIMBER, -16, -6, 32, 48);
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();

        // The reach line: where the hands are.
        g.fill(ax + 40, ay + LINE - MARGIN, ax + AREA_W - 40, ay + LINE + MARGIN, 0x22FFFFFF);
        g.fill(ax + 40, ay + LINE - MARGIN, ax + AREA_W - 40, ay + LINE - MARGIN + 1, 0x70FFFFFF);
        g.fill(ax + 40, ay + LINE + MARGIN - 1, ax + AREA_W - 40, ay + LINE + MARGIN, 0x70FFFFFF);
        g.disableScissor();

        // Altitude: a gauge on the right edge.
        double part = Math.max(0, Math.min(1, climbed / top));
        int gx = ax + AREA_W - 10, g0 = ay + 18, g1 = ay + AREA_H - 8;
        g.fill(gx - 1, g0 - 1, gx + 4, g1 + 1, Draw.EDGE);
        g.fill(gx, g1 - (int) ((g1 - g0) * part), gx + 3, g1, Draw.GREEN);
        g.fill(gx - 3, g0 - 1, gx + 6, g0, Draw.YELLOW);

        String left = T.f("holds") + holds + " / " + liveHolds, right = T.f("slips_2") + faults + " / 3";
        g.fill(ax + 2, ay + 2, ax + 10 + Draw.textFont().width(left), ay + 13, 0xA0101418);
        g.fill(ax + AREA_W - 10 - Draw.textFont().width(right), ay + 2, ax + AREA_W - 2, ay + 13, 0xA0101418);
        text(g, left, ax + 6, ay + 4, holds > 0 ? Draw.GREEN : Draw.TEXT);
        text(g, right, ax + AREA_W - 6 - Draw.textFont().width(right), ay + 4, faults > 0 ? Draw.ALERT : Draw.GREY);
        if (feedbackTicks > 0 && feedback != null) Draw.centre(g, feedback, ax + AREA_W / 2, ay + LINE - 24 - (18 - feedbackTicks) / 2, feedbackColour);
        if (flash > 0) {
            g.fill(ax, ay, ax + AREA_W, ay + 1, Draw.ALERT);
            g.fill(ax, ay + AREA_H - 1, ax + AREA_W, ay + AREA_H, Draw.ALERT);
            g.fill(ax, ay, ax + 1, ay + AREA_H, Draw.ALERT);
            g.fill(ax + AREA_W - 1, ay, ax + AREA_W, ay + AREA_H, Draw.ALERT);
        }
    }

    // A vine hanging along the trunk: a thick twisted cord with big leaves, from its bottom (py) up its length, and a
    // glowing knot at the bottom where it is grabbed.
    void vine(GuiGraphics g, int i, int py, boolean active) {
        int x = ox + (side[i] == 0 ? TRUNK_X - 8 : TRUNK_X + TRUNK_W + 3), top = py - length[i];
        if (active) g.fill(x - 6, top, x + 11, py + 4, 0x5040E070);
        for (int y = top; y < py; y++) {
            int wiggle = (int) Math.round(Math.sin(y * 0.3) * 1.5);
            g.fill(x + wiggle - 1, y, x + wiggle + 6, y + 1, 0xFF13240E);
            g.fill(x + wiggle, y, x + wiggle + 5, y + 1, (y & 3) < 2 ? 0xFF6FC24A : 0xFF4E9A34);
            g.fill(x + wiggle + 1, y, x + wiggle + 2, y + 1, 0xFFA8E07A);
            if ((y - top) % 8 == 0) {
                int dir = ((y - top) / 8) % 2 == 0 ? -1 : 1, lx = x + wiggle + (dir < 0 ? -5 : 5);
                g.fill(lx - 1, y - 1, lx + 6, y + 3, 0xFF13240E);
                g.fill(lx, y, lx + 5, y + 2, 0xFF8AD65A);
            }
        }
        int pulse = tic % 20 < 10 ? 0xFFF2F08A : 0xFFD8E870;
        g.fill(x - 2, py - 2, x + 7, py + 4, 0xFF13240E);
        g.fill(x - 1, py - 1, x + 6, py + 3, pulse);
    }

    @Override
    String instruction() {
        return T.f("the_canopy_keeps_its_secrets");
    }

    @Override
    Control[] controls() {
        return new Control[] {
                new Control(T.f("grab"), InputConstants.KEY_A, InputConstants.KEY_D, Draw.OR, Draw.ARROW_LEFT, Draw.ARROW_RIGHT, Draw.OR, Draw.MOUSE),
                new Control(T.f("vine_hold"), Draw.MOUSE)};
    }
}
