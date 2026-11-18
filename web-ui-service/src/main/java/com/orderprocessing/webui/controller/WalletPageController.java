package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.WalletBalanceView;
import com.orderprocessing.webui.dto.WalletEntryView;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/app/wallet")
public class WalletPageController {
    private final AuthenticatedPlatformClient client;

    public WalletPageController(AuthenticatedPlatformClient client) {
        this.client = client;
    }

    @GetMapping
    public String ledger(Model model) {
        WalletBalanceView balance = client.myWalletBalance();
        List<WalletEntryView> entries = balance == null ? List.of() : client.myWalletEntries();
        model.addAttribute("balance", balance);
        model.addAttribute("entries", entries);
        return "wallet/index";
    }
}
