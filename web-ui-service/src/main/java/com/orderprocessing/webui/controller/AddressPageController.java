package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.AddressView;
import com.orderprocessing.webui.exception.BackendClientException;
import com.orderprocessing.webui.form.AddressForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/app/addresses")
public class AddressPageController {

    private final AuthenticatedPlatformClient client;

    public AddressPageController(AuthenticatedPlatformClient client) {
        this.client = client;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("addresses", client.addresses());
        return "addresses/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("addressForm")) {
            model.addAttribute("addressForm", new AddressForm());
        }
        model.addAttribute("editing", false);
        return "addresses/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute AddressForm addressForm, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", false);
            return "addresses/form";
        }
        try {
            client.createAddress(addressForm);
            redirect.addFlashAttribute("success", "Address saved");
            return "redirect:/app/addresses";
        } catch (BackendClientException exception) {
            apply(exception, binding);
            model.addAttribute("editing", false);
            return "addresses/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        AddressView address = client.address(id);
        if (!model.containsAttribute("addressForm")) {
            model.addAttribute("addressForm", form(address));
        }
        model.addAttribute("editing", true);
        model.addAttribute("addressId", id);
        return "addresses/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @Valid @ModelAttribute AddressForm addressForm, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", true);
            model.addAttribute("addressId", id);
            return "addresses/form";
        }
        try {
            client.updateAddress(id, addressForm);
            redirect.addFlashAttribute("success", "Address saved");
            return "redirect:/app/addresses";
        } catch (BackendClientException exception) {
            apply(exception, binding);
            model.addAttribute("editing", true);
            model.addAttribute("addressId", id);
            return "addresses/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        client.deleteAddress(id);
        redirect.addFlashAttribute("success", "Address deleted. Orders already placed keep their ship-to snapshot.");
        return "redirect:/app/addresses";
    }

    @PostMapping("/{id}/default")
    public String makeDefault(@PathVariable UUID id, RedirectAttributes redirect) {
        AddressView address = client.address(id);
        AddressForm form = form(address);
        form.setDefaultAddress(true);
        client.updateAddress(id, form);
        redirect.addFlashAttribute("success", "Default address updated");
        return "redirect:/app/addresses";
    }

    private AddressForm form(AddressView address) {
        AddressForm form = new AddressForm();
        form.setRecipient(address.recipient());
        form.setLine1(address.line1());
        form.setLine2(address.line2());
        form.setCity(address.city());
        form.setRegion(address.region());
        form.setPostalCode(address.postalCode());
        form.setPhone(address.phone());
        form.setDefaultAddress(address.defaultAddress());
        return form;
    }

    private void apply(BackendClientException exception, BindingResult binding) {
        exception.getFieldErrors().forEach((field, message) -> binding.rejectValue(field, "backend", message));
        if (exception.getFieldErrors().isEmpty()) {
            binding.reject("backend", exception.getMessage());
        }
    }
}
