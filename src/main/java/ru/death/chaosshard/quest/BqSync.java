package ru.death.chaosshard.quest;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.storage.DBEntry;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/**
 * Better Questing сам ловит достижение, только если квест уже открыт в момент получения.
 * Здесь — то же, что кнопка «Проверить»: открытые квесты с задачей-достижением проверяются раз в две секунды.
 */
final class BqSync {

    static void detect(EntityPlayerMP player) {
        UUID uuid = QuestingAPI.getQuestingUUID(player);
        for (DBEntry<IQuest> entry : QuestingAPI.getAPI(ApiReference.QUEST_DB).getEntries()) {
            IQuest quest = entry.getValue();
            if (quest.isComplete(uuid) || !quest.isUnlocked(uuid)) {
                continue;
            }
            for (DBEntry<ITask> t : quest.getTasks().getEntries()) {
                ITask task = t.getValue();
                if (!task.isComplete(uuid) && "advancement".equals(task.getFactoryID().getPath())) {
                    quest.detect(player);
                    break;
                }
            }
        }
    }

    private BqSync() {
    }
}
