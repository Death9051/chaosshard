package ru.death.chaosshard.cutscene;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Катсцена: камера летит по ключевым кадрам (точка и куда смотреть), внизу — субтитры.
 * Всё в мировых координатах; клиент получает её целиком одним пакетом и играет сам,
 * сервер в это время держит игрока на месте и ведёт свой сценарий по тикам.
 */
public final class Cutscene {

    /** Ключевой кадр камеры. cut — резкая склейка: к этому кадру не летим, а прыгаем. */
    public static final class Key {
        public final int tick;
        public final Vec3d pos, look;
        public final boolean cut;

        public Key(int tick, Vec3d pos, Vec3d look, boolean cut) {
            this.tick = tick;
            this.pos = pos;
            this.look = look;
            this.cut = cut;
        }
    }

    /** Строка субтитров: кто говорит (ключ перевода, может быть пустым) и что. */
    public static final class Line {
        public final int from, to;
        public final String speaker, text;
        /** Цвет имени говорящего (0xRRGGBB). */
        public final int color;

        public Line(int from, int to, String speaker, String text, int color) {
            this.from = from;
            this.to = to;
            this.speaker = speaker == null ? "" : speaker;
            this.text = text;
            this.color = color;
        }
    }

    public final String id;
    public final int length;
    public final List<Key> keys = new ArrayList<>();
    public final List<Line> lines = new ArrayList<>();

    public Cutscene(String id, int length) {
        this.id = id;
        this.length = length;
    }

    public Cutscene key(int tick, Vec3d pos, Vec3d look) {
        keys.add(new Key(tick, pos, look, false));
        return this;
    }

    public Cutscene cut(int tick, Vec3d pos, Vec3d look) {
        keys.add(new Key(tick, pos, look, true));
        return this;
    }

    public Cutscene line(int from, int to, String speaker, String text, int color) {
        lines.add(new Line(from, to, speaker, text, color));
        return this;
    }

    // ------------------------------------------------------------------ камера (общая для клиента и проверок)

    /** Положение камеры и точка взгляда в момент t (тики, дробные). */
    public Vec3d[] at(double t) {
        if (keys.isEmpty()) {
            return new Vec3d[]{Vec3d.ZERO, new Vec3d(0, 0, 1)};
        }
        Key a = keys.get(0);
        if (t <= a.tick) {
            return new Vec3d[]{a.pos, a.look};
        }
        for (int i = 1; i < keys.size(); i++) {
            Key b = keys.get(i);
            if (t < b.tick) {
                if (b.cut) {
                    return new Vec3d[]{a.pos, a.look};   // держим кадр до склейки
                }
                double f = (t - a.tick) / Math.max(1.0, b.tick - a.tick);
                Key p0 = i >= 2 && !a.cut ? keys.get(i - 2) : a;
                Key p3 = i + 1 < keys.size() && !keys.get(i + 1).cut ? keys.get(i + 1) : b;
                return new Vec3d[]{spline(p0.pos, a.pos, b.pos, p3.pos, f), lerp(a.look, b.look, smooth(f))};
            }
            a = b;
        }
        return new Vec3d[]{a.pos, a.look};
    }

    static double smooth(double f) {
        return f * f * (3 - 2 * f);
    }

    static Vec3d lerp(Vec3d a, Vec3d b, double f) {
        return new Vec3d(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.z + (b.z - a.z) * f);
    }

    /** Catmull-Rom: плавный пролёт через точки a и b с учётом соседей. */
    static Vec3d spline(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return new Vec3d(cr(p0.x, p1.x, p2.x, p3.x, t, t2, t3), cr(p0.y, p1.y, p2.y, p3.y, t, t2, t3), cr(p0.z, p1.z, p2.z, p3.z, t, t2, t3));
    }

    private static double cr(double p0, double p1, double p2, double p3, double t, double t2, double t3) {
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    // ------------------------------------------------------------------ сеть

    public void write(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, id);
        buf.writeInt(length);
        buf.writeShort(keys.size());
        for (Key k : keys) {
            buf.writeInt(k.tick);
            buf.writeDouble(k.pos.x);
            buf.writeDouble(k.pos.y);
            buf.writeDouble(k.pos.z);
            buf.writeDouble(k.look.x);
            buf.writeDouble(k.look.y);
            buf.writeDouble(k.look.z);
            buf.writeBoolean(k.cut);
        }
        buf.writeShort(lines.size());
        for (Line l : lines) {
            buf.writeInt(l.from);
            buf.writeInt(l.to);
            ByteBufUtils.writeUTF8String(buf, l.speaker);
            ByteBufUtils.writeUTF8String(buf, l.text);
            buf.writeInt(l.color);
        }
    }

    public static Cutscene read(ByteBuf buf) {
        Cutscene c = new Cutscene(ByteBufUtils.readUTF8String(buf), buf.readInt());
        int n = buf.readShort();
        for (int i = 0; i < n; i++) {
            int t = buf.readInt();
            Vec3d p = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3d l = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            c.keys.add(new Key(t, p, l, buf.readBoolean()));
        }
        n = buf.readShort();
        for (int i = 0; i < n; i++) {
            c.lines.add(new Line(buf.readInt(), buf.readInt(), ByteBufUtils.readUTF8String(buf), ByteBufUtils.readUTF8String(buf), buf.readInt()));
        }
        return c;
    }
}
