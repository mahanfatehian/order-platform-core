package com.orderprocessing.webui.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class QuantityForm {
    @NotNull @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity = 1;
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
