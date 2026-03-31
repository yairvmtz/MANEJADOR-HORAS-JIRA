package com.personal.jiralog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Worklog {
    private String id;
    private String issueId;
    private Author author;
    private Object comment;
    private String started; // ISO 8601
    private String startDate; // Fecha de inicio de la actividad (YYYY-MM-DD)
    private String issueKey; // Clave del issue (ej. PROJ-123)
    private String issueSummary; // Resumen del issue
    private int timeSpentSeconds;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIssueId() { return issueId; }
    public void setIssueId(String issueId) { this.issueId = issueId; }

    public String getIssueKey() { return issueKey; }
    public void setIssueKey(String issueKey) { this.issueKey = issueKey; }

    public String getIssueSummary() { return issueSummary; }
    public void setIssueSummary(String issueSummary) { this.issueSummary = issueSummary; }

    public Author getAuthor() { return author; }
    public void setAuthor(Author author) { this.author = author; }

    public Object getComment() { return comment; }
    public void setComment(Object comment) { this.comment = comment; }

    public String getStarted() { return started; }
    public void setStarted(String started) { this.started = started; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public int getTimeSpentSeconds() { return timeSpentSeconds; }
    public void setTimeSpentSeconds(int timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        private String self;
        private String accountId;
        private String emailAddress;
        private String displayName;

        public String getSelf() { return self; }
        public void setSelf(String self) { this.self = self; }

        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }

        public String getEmailAddress() { return emailAddress; }
        public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }
}
