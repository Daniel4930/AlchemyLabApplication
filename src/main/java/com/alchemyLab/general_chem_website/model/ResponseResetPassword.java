package com.alchemyLab.general_chem_website.model;

public class ResponseResetPassword {
    private String token;
    private String email;
    private String password;

    public String getToken() {return token;}
    public String getEmail() {return email;}
    public String getPassword() {return password;}
    public void setToken(String token) {this.token = token;}
    public void setEmail(String email) {this.email = email;}
    public void setPassword(String password) {this.password = password;}
}
