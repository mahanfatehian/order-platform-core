package com.orderprocessing.webui.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginForm {
    @NotBlank(message = "Enter your username")
    private String username;
    @NotBlank(message = "Enter your password")
    private String password;
    /**
     * Only required once the attempt counter escalates, so the constraint is applied by the controller rather
     * than declared here; bean validation cannot express "required only sometimes".
     */
    @Size(max = 32) private String captcha;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getCaptcha() { return captcha; }
    public void setCaptcha(String captcha) { this.captcha = captcha; }
}
