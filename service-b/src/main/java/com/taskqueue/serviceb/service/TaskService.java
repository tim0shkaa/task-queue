package com.taskqueue.serviceb.service;

import com.taskqueue.serviceb.model.Task;
import com.taskqueue.serviceb.model.TaskWrapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private static final int TASK_COUNT = 100_000;
    private static final String[] CATEGORIES = {"Development", "Testing", "Design", "Documentation", "Review"};
    private static final String[] USERS = {"user1", "user2", "user3", "user4", "user5"};

    public Flux<Task> getUserTasks(String userId) {
        List<Task> generatedTasks = generateTasks();

        List<TaskWrapper> wrappedTasks = wrapTasks(generatedTasks);

        List<TaskWrapper> filteredOnce = firstFilter(wrappedTasks, userId);

        List<TaskWrapper> filteredTwice = secondFilter(filteredOnce);

        List<TaskWrapper> filteredThrice = thirdFilter(filteredTwice);

        List<TaskWrapper> sortedOnce = firstSort(filteredThrice);

        List<TaskWrapper> sortedTwice = secondSort(sortedOnce);

        Map<String, List<TaskWrapper>> grouped = groupTasks(sortedTwice);

        List<Task> finalTasks = unwrapTasks(grouped);

        return Flux.fromIterable(finalTasks);
    }

    private List<Task> generateTasks() {
        List<Task> tasks = new ArrayList<>(TASK_COUNT);
        Random random = new Random();

        for (long i = 0; i < TASK_COUNT; i++) {
            Task task = new Task();
            task.setId(i);
            task.setUserId(USERS[random.nextInt(USERS.length)]);
            task.setTitle("Task " + i);
            task.setDescription("Description for task " + i);
            task.setStatus(Task.TaskStatus.values()[random.nextInt(Task.TaskStatus.values().length)]);
            task.setPriority(Task.TaskPriority.values()[random.nextInt(Task.TaskPriority.values().length)]);
            task.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(30)));
            task.setDueDate(LocalDateTime.now().plusDays(random.nextInt(60)));
            task.setEstimatedHours(random.nextInt(20) + 1);
            task.setCategory(CATEGORIES[random.nextInt(CATEGORIES.length)]);
            task.setAssignee("assignee" + random.nextInt(10));
            tasks.add(task);
        }

        return tasks;
    }

    private List<TaskWrapper> wrapTasks(List<Task> tasks) {
        List<TaskWrapper> wrapped = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            wrapped.add(new TaskWrapper(task));
        }
        return wrapped;
    }

    private List<TaskWrapper> firstFilter(List<TaskWrapper> tasks, String userId) {
        return tasks.stream()
                .filter(wrapper -> wrapper.getTask().getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    private List<TaskWrapper> secondFilter(List<TaskWrapper> tasks) {
        return tasks.stream()
                .filter(wrapper -> wrapper.getTask().getStatus() != Task.TaskStatus.CANCELLED)
                .collect(Collectors.toList());
    }

    private List<TaskWrapper> thirdFilter(List<TaskWrapper> tasks) {
        return tasks.stream()
                .filter(wrapper -> wrapper.getTask().getEstimatedHours() > 0)
                .collect(Collectors.toList());
    }

    private List<TaskWrapper> firstSort(List<TaskWrapper> tasks) {
        List<TaskWrapper> sorted = new ArrayList<>(tasks);
        sorted.sort(new Comparator<TaskWrapper>() {
            @Override
            public int compare(TaskWrapper w1, TaskWrapper w2) {
                return Integer.compare(w1.getSortKey1(), w2.getSortKey1());
            }
        });
        return sorted;
    }

    private List<TaskWrapper> secondSort(List<TaskWrapper> tasks) {
        List<TaskWrapper> sorted = new ArrayList<>(tasks);
        sorted.sort(new Comparator<TaskWrapper>() {
            @Override
            public int compare(TaskWrapper w1, TaskWrapper w2) {
                int priorityCompare = w1.getTask().getPriority().compareTo(w2.getTask().getPriority());
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                return Integer.compare(w2.getSortKey2(), w1.getSortKey2());
            }
        });
        return sorted;
    }

    private Map<String, List<TaskWrapper>> groupTasks(List<TaskWrapper> tasks) {
        return tasks.stream()
                .collect(Collectors.groupingBy(
                        wrapper -> wrapper.getTask().getCategory(),
                        Collectors.toList()
                ));
    }

    private List<Task> unwrapTasks(Map<String, List<TaskWrapper>> groupedTasks) {
        List<Task> result = new ArrayList<>();
        for (Map.Entry<String, List<TaskWrapper>> entry : groupedTasks.entrySet()) {
            for (TaskWrapper wrapper : entry.getValue()) {
                result.add(wrapper.getTask());
            }
        }
        return result;
    }

}
