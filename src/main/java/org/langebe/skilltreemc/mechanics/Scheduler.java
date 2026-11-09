package org.langebe.skilltreemc.mechanics;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Runs code later or repeatedly on the server thread – used for effects that last a
 * while (e.g. the expanding Demon Shockwave ring or the Buff Shower zone).
 *
 * <pre>{@code
 * // 5 times, every 20 ticks (= once per second for 5 s), starting now:
 * Scheduler.repeat(20, 5, run -> { ... });
 * }</pre>
 *
 * Tasks are not saved – they are lost on server restart, which is fine for short effects.
 */
public final class Scheduler {

    private static final List<Task> TASKS = new ArrayList<>();

    private Scheduler() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Scheduler::tick);
    }

    /** Runs {@code action} once after {@code delayTicks}. */
    public static void later(int delayTicks, Runnable action) {
        TASKS.add(new Task(delayTicks, 0, 1, run -> action.run()));
    }

    /**
     * Runs {@code action} {@code times} times, every {@code intervalTicks}, the first time
     * immediately (next tick). The action receives the run index (0, 1, 2, ...).
     */
    public static void repeat(int intervalTicks, int times, IntConsumer action) {
        TASKS.add(new Task(0, intervalTicks, times, action));
    }

    private static void tick(MinecraftServer server) {
        // Copy first: an action may schedule new tasks.
        List<Task> due = new ArrayList<>();
        Iterator<Task> iterator = TASKS.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (--task.wait <= 0) {
                due.add(task);
                task.wait = task.interval;
                if (++task.run >= task.times) {
                    iterator.remove();
                }
            }
        }
        for (Task task : due) {
            task.action.accept(task.run - 1);
        }
    }

    private static final class Task {
        int wait;
        final int interval;
        final int times;
        int run;
        final IntConsumer action;

        Task(int wait, int interval, int times, IntConsumer action) {
            this.wait = wait;
            this.interval = interval;
            this.times = times;
            this.action = action;
        }
    }
}
