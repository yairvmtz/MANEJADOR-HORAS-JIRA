package com.personal.jiralog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssue {
    private String id;
    private String key;
    private Fields fields;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public Fields getFields() { return fields; }
    public void setFields(Fields fields) { this.fields = fields; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fields {
        private String summary;
        private Status status;
        private String duedate;
        private String startdate;
        private String startDate;
        private String customfield_10015;
        private String customfield_10715;
        private TimeTracking timetracking;
        private WorklogResponse worklog;

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }

        public Status getStatus() { return status; }
        public void setStatus(Status status) { this.status = status; }

        public String getDuedate() { return duedate; }
        public void setDuedate(String duedate) { this.duedate = duedate; }

        public String getStartdate() { return startdate; }
        public void setStartdate(String startdate) { this.startdate = startdate; }

        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }

        public String getCustomfield_10015() { return customfield_10015; }
        public void setCustomfield_10015(String customfield_10015) { this.customfield_10015 = customfield_10015; }

        public String getCustomfield_10715() { return customfield_10715; }
        public void setCustomfield_10715(String customfield_10715) { this.customfield_10715 = customfield_10715; }

        public TimeTracking getTimetracking() { return timetracking; }
        public void setTimetracking(TimeTracking timetracking) { this.timetracking = timetracking; }

        public WorklogResponse getWorklog() { return worklog; }
        public void setWorklog(WorklogResponse worklog) { this.worklog = worklog; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TimeTracking {
        private String originalEstimate;
        private String timeSpent;
        private String remainingEstimate;
        private int originalEstimateSeconds;
        private int timeSpentSeconds;
        private int remainingEstimateSeconds;

        public String getOriginalEstimate() { return originalEstimate; }
        public void setOriginalEstimate(String originalEstimate) { this.originalEstimate = originalEstimate; }

        public String getTimeSpent() { return timeSpent; }
        public void setTimeSpent(String timeSpent) { this.timeSpent = timeSpent; }

        public String getRemainingEstimate() { return remainingEstimate; }
        public void setRemainingEstimate(String remainingEstimate) { this.remainingEstimate = remainingEstimate; }

        public int getOriginalEstimateSeconds() { return originalEstimateSeconds; }
        public void setOriginalEstimateSeconds(int originalEstimateSeconds) { this.originalEstimateSeconds = originalEstimateSeconds; }

        public int getTimeSpentSeconds() { return timeSpentSeconds; }
        public void setTimeSpentSeconds(int timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }

        public int getRemainingEstimateSeconds() { return remainingEstimateSeconds; }
        public void setRemainingEstimateSeconds(int remainingEstimateSeconds) { this.remainingEstimateSeconds = remainingEstimateSeconds; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorklogResponse {
        private List<Worklog> worklogs;
        public List<Worklog> getWorklogs() { return worklogs; }
        public void setWorklogs(List<Worklog> worklogs) { this.worklogs = worklogs; }
    }
}
