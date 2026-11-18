package com.orderprocessing.webui.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddressForm {
    @NotBlank @Size(max = 200) private String recipient;
    @NotBlank @Size(max = 200) private String line1;
    @Size(max = 200) private String line2;
    @NotBlank @Size(max = 100) private String city;
    @NotBlank @Size(max = 100) private String region;
    @NotBlank @Size(max = 20) private String postalCode;
    @NotBlank @Size(max = 32) private String phone;
    private boolean defaultAddress;

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getLine1() { return line1; }
    public void setLine1(String line1) { this.line1 = line1; }
    public String getLine2() { return line2; }
    public void setLine2(String line2) { this.line2 = line2; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(boolean defaultAddress) { this.defaultAddress = defaultAddress; }
}
