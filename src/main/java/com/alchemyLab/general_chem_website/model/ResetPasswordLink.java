package com.alchemyLab.general_chem_website.model;

public class ResetPasswordLink {
    private String link;
    private String expireTime;

    public String getLink() {return link;}
    public String getExpireTime() {return expireTime;}
    public void setLink(String link) {this.link = link;}
    public void setExpireTime(String expireTime) {this.expireTime = expireTime;}
}
