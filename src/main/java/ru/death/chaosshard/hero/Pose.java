package ru.death.chaosshard.hero;

/**
 * Позы боссов: сервер говорит, что босс сейчас делает, клиентская модель встаёт в позу.
 * Номера пишутся в синхронизируемое поле — только в конец.
 */
public final class Pose {

    public static final int NONE = 0;
    /** Правая рука с оружием вскинута вверх (клинок к небу, посох вверх). */
    public static final int RAISE = 1;
    /** Рывок: корпус вперёд, оружие наперевес. */
    public static final int DASH = 2;
    /** На коленях, руки сложены в молитве. */
    public static final int KNEEL_PRAY = 3;
    /** Правая рука вытянута вперёд — бросок заклинания. */
    public static final int CAST = 4;
    /** Открыт: руки опущены, голова поникла. */
    public static final int STAGGER = 5;
    /** Замах: обе руки над головой. */
    public static final int WINDUP = 6;
    /** Удар сверху вниз: руки впереди внизу, корпус согнут. */
    public static final int CHOP = 7;
    /** Обе руки вверх и в стороны — свет с небес. */
    public static final int RAISE_BOTH = 8;
    /** Играет на лютне. */
    public static final int LUTE = 9;
    /** Хватается за горло. */
    public static final int CHOKE = 10;
    /** Стоя, руки сложены у груди (щит молитвы). */
    public static final int PRAY = 11;
    /** На одном колене, голова опущена (поклон). */
    public static final int KNEEL = 12;
    /** Обе руки вперёд и вниз — руны под ноги. */
    public static final int CAST_DOWN = 13;
    /** Гибель Каэля: на колене, опирается на вонзённый перед собой клинок, голова опущена. */
    public static final int KNEEL_SWORD = 14;
    /** Гибель Лиана: на коленях, доигрывает на лютне. */
    public static final int KNEEL_LUTE = 15;
    /** Гибель Элис: на колене, клинок вскинут в последнем салюте. */
    public static final int KNEEL_SALUTE = 16;

    /** Позы на коленях: модель опускается. */
    public static boolean kneeling(int pose) {
        return pose == KNEEL || pose == KNEEL_PRAY || pose == KNEEL_SWORD || pose == KNEEL_LUTE || pose == KNEEL_SALUTE;
    }

    /** Сущность, у которой есть поза. */
    public interface Posed {
        int pose();
    }

    private Pose() {
    }
}
