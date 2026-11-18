package com.orderprocessing.webui.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CategoryForm {
    @NotBlank @Size(max = 255) private String name;
    private String returnTo;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getReturnTo() { return returnTo; }
    public void setReturnTo(String returnTo) { this.returnTo = returnTo; }
}
