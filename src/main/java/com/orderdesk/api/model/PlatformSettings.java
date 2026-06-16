package com.orderdesk.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PlatformSettings {
    @Id
    private Long id = 1L;

    private boolean siteOpen = true;
    private boolean globalNoticeActive = false;
    private String globalNoticeTitle = "Aviso";
    private String globalNoticeMessage = "";
    private String globalNoticeType = "INFO";
    private String closedTitle = "OrderDesk esta fechado no momento";
    private String closedMessage = "Voltaremos em breve.";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public boolean isSiteOpen() { return siteOpen; }
    public void setSiteOpen(boolean siteOpen) { this.siteOpen = siteOpen; }
    public boolean isGlobalNoticeActive() { return globalNoticeActive; }
    public void setGlobalNoticeActive(boolean globalNoticeActive) { this.globalNoticeActive = globalNoticeActive; }
    public String getGlobalNoticeTitle() { return globalNoticeTitle; }
    public void setGlobalNoticeTitle(String globalNoticeTitle) { this.globalNoticeTitle = globalNoticeTitle; }
    public String getGlobalNoticeMessage() { return globalNoticeMessage; }
    public void setGlobalNoticeMessage(String globalNoticeMessage) { this.globalNoticeMessage = globalNoticeMessage; }
    public String getGlobalNoticeType() { return globalNoticeType; }
    public void setGlobalNoticeType(String globalNoticeType) { this.globalNoticeType = globalNoticeType; }
    public String getClosedTitle() { return closedTitle; }
    public void setClosedTitle(String closedTitle) { this.closedTitle = closedTitle; }
    public String getClosedMessage() { return closedMessage; }
    public void setClosedMessage(String closedMessage) { this.closedMessage = closedMessage; }
}
