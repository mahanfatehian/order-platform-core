package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.UserView;
import com.orderprocessing.webui.exception.BackendClientException;
import com.orderprocessing.webui.form.ChangePasswordForm;
import com.orderprocessing.webui.form.ProfileForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/app/profile")
public class ProfileController {
    private final AuthenticatedPlatformClient client;
    public ProfileController(AuthenticatedPlatformClient client) { this.client = client; }

    @GetMapping
    public String profile(Model model) {
        UserView user = client.profile();
        model.addAttribute("profile", user);
        if (!model.containsAttribute("profileForm")) {
            ProfileForm form = new ProfileForm(); form.setEmail(user.email());
            form.setFirstName(user.firstName()); form.setLastName(user.lastName());
            model.addAttribute("profileForm", form);
        }
        if (!model.containsAttribute("changePasswordForm")) model.addAttribute("changePasswordForm", new ChangePasswordForm());
        return "profile/index";
    }

    @PostMapping
    public String update(@Valid @ModelAttribute ProfileForm profileForm, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) { model.addAttribute("profile", client.profile()); model.addAttribute("changePasswordForm", new ChangePasswordForm()); return "profile/index"; }
        try {
            client.updateProfile(profileForm); redirect.addFlashAttribute("success", "Profile changes saved");
            return "redirect:/app/profile";
        } catch (BackendClientException exception) {
            exception.getFieldErrors().forEach((field, message) -> binding.rejectValue(field, "backend", message));
            if (exception.getFieldErrors().isEmpty()) binding.reject("backend", exception.getMessage());
            model.addAttribute("profile", client.profile()); model.addAttribute("changePasswordForm", new ChangePasswordForm());
            return "profile/index";
        }
    }

    @PostMapping("/change-password")
    public String changePassword(@Valid @ModelAttribute ChangePasswordForm changePasswordForm, BindingResult binding,
                                 Model model, RedirectAttributes redirect) {
        // Only compare once the fields are known to be present. @NotBlank records a binding error for a missing
        // newPassword, but this ran first and dereferenced it, turning an ordinary incomplete form into a 500.
        if (!binding.hasFieldErrors("newPassword")
                && !java.util.Objects.equals(changePasswordForm.getNewPassword(),
                                             changePasswordForm.getConfirmPassword())) {
            binding.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        }
        if (binding.hasErrors()) {
            model.addAttribute("profile", client.profile()); model.addAttribute("profileForm", new ProfileForm());
            return "profile/index";
        }
        try {
            client.changePassword(changePasswordForm);
            redirect.addFlashAttribute("success", "Password changed. Other sessions have been revoked.");
            return "redirect:/app/profile";
        } catch (BackendClientException exception) {
            changePasswordForm.setCurrentPassword(null);
            changePasswordForm.setNewPassword(null);
            changePasswordForm.setConfirmPassword(null);
            binding.reject("backend", exception.getMessage());
            model.addAttribute("profile", client.profile()); model.addAttribute("profileForm", new ProfileForm());
            return "profile/index";
        }
    }
}
