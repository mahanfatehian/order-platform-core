package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.OrderView;
import com.orderprocessing.webui.dto.PageResponse;
import com.orderprocessing.webui.dto.PaginationLinks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryControllerTest {

    @Mock
    private AuthenticatedPlatformClient client;

    @InjectMocks
    private DeliveryController controller;

    @Test
    void pagesPackagedAndShippedQueuesIndependently() {
        PageResponse<OrderView> packaged = new PageResponse<>(List.of(), 2, 20, 80, 4, false, false);
        PageResponse<OrderView> shipped = new PageResponse<>(List.of(), 1, 20, 45, 3, false, false);
        when(client.fulfillmentOrders(2, 20, "PACKAGED")).thenReturn(packaged);
        when(client.fulfillmentOrders(1, 20, "SHIPPED")).thenReturn(shipped);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("admin/delivery/index", controller.dashboard(2, 1, model));

        verify(client).fulfillmentOrders(2, 20, "PACKAGED");
        verify(client).fulfillmentOrders(1, 20, "SHIPPED");
        PaginationLinks packagedLinks = (PaginationLinks) model.get("packagedPagination");
        PaginationLinks shippedLinks = (PaginationLinks) model.get("shippedPagination");
        assertEquals("/admin/delivery?shippedPage=1&packagedPage=1", packagedLinks.previousUrl());
        assertEquals("/admin/delivery?shippedPage=1&packagedPage=3", packagedLinks.nextUrl());
        assertEquals("/admin/delivery?packagedPage=2&shippedPage=0", shippedLinks.previousUrl());
        assertEquals("/admin/delivery?packagedPage=2&shippedPage=2", shippedLinks.nextUrl());
    }

    @Test
    void recordsALateTrackingReferenceAgainstTheShippedOrder() {
        UUID id = UUID.randomUUID();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        assertEquals("redirect:/admin/delivery", controller.recordTracking(id, "  TRACK-9  ", redirect));

        verify(client).shipOrder(id, "TRACK-9");
        assertEquals("Tracking reference recorded.", redirect.getFlashAttributes().get("success"));
    }

    @Test
    void doesNotCallTheBackendForABlankTrackingReference() {
        UUID id = UUID.randomUUID();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        assertEquals("redirect:/admin/delivery", controller.recordTracking(id, "   ", redirect));

        verify(client, never()).shipOrder(any(), any());
        assertEquals("Enter a tracking reference before saving it.", redirect.getFlashAttributes().get("warning"));
    }
}
