package ru.death.chaosshard.thread;

/**
 * Нить — связь Осколка с Лордом и Планом Хаоса.
 * Хранит накопленные очки; уровень считается из очков по порогам из конфига.
 */
public interface IThread {

    int getPoints();

    void setPoints(int points);

    /** Последний уровень, за который уже выданы этапы и сообщения. */
    int getAnnouncedLevel();

    void setAnnouncedLevel(int level);
}
