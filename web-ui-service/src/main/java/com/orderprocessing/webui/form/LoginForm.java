package com.orderprocessing.webui.form;

import jakarta.validation.constraints.NotBlank;

public class LoginForm {
    @NotBlank(message = "Enter your username")
    private String username;
    @NotBlank(message = "Enter your password")
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
