package com.orderprocessing.webui.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class InventoryForm {
    @NotNull @Min(0) private Integer quantity;
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
