package com.personal.jiralog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraWorklogResponse {
    private List<Worklog> worklogs;

    public List<Worklog> getWorklogs() {
        return worklogs;
    }

    public void setWorklogs(List<Worklog> worklogs) {
        this.worklogs = worklogs;
    }
}
