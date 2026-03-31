package com.personal.jiralog.model;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ExcelActivity {
    private String parentKey;
    private String summary;
    private String activityName;
    private String estimate;
    private LocalDate startDate;
    private LocalDate targetDate;
    private String status;
    private int rowIndex;
    private String createdKey;
}
