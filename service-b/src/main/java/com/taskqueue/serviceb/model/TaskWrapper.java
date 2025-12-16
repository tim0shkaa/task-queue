package com.taskqueue.serviceb.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskWrapper {

    private Task task;
    private String wrappedId;
    private Integer sortKey1;
    private Integer sortKey2;
    private String filterTag;
    private Long timestamp;

    public TaskWrapper(Task task) {
        this.task = task;
        this.wrappedId = "WRAP_" + task.getId();
        this.sortKey1 = task.getId().intValue();
        this.sortKey2 = task.getEstimatedHours();
        this.filterTag = task.getCategory() + "_" + task.getStatus();
        this.timestamp = System.nanoTime();
    }

}
