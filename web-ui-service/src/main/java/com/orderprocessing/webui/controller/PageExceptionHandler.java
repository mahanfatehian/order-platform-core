package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.exception.BackendClientException;
import com.orderprocessing.webui.exception.LogoutRevocationException;
import com.orderprocessing.webui.exception.RateLimitedException;
import com.orderprocessing.webui.exception.SessionExpiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class PageExceptionHandler {
    @ExceptionHandler(SessionExpiredException.class)
    public ModelAndView expired(SessionExpiredException exception, HttpServletRequest request,
                                HttpServletResponse response, RedirectAttributes redirect) {
        if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) {
            response.setHeader("HX-Redirect", request.getContextPath() + "/login");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            ModelAndView unauthorized = new ModelAndView();
            return unauthorized;
        }
        redirect.addFlashAttribute("warning", "Your session expired. Please sign in again.");
        return new ModelAndView("redirect:/login");
    }

    @ExceptionHandler(BackendClientException.class)
    public ModelAndView backend(BackendClientException exception, HttpServletRequest request) {
        if (exception.getStatus().value() == 404) return new ModelAndView("error/404", HttpStatus.NOT_FOUND);
        if (exception.getStatus().value() == 403) return new ModelAndView("error/403", HttpStatus.FORBIDDEN);
        ModelAndView view = new ModelAndView("error/service-unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        view.addObject("serviceMessage", "A platform service could not complete this request. No changes were made.");
        view.addObject("retryPath", retryPath(request));
        return view;
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ModelAndView unavailable(ResourceAccessException exception, HttpServletRequest request) {
        ModelAndView view = new ModelAndView("error/service-unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        view.addObject("serviceMessage", "The platform is temporarily unavailable. Try again shortly.");
        view.addObject("retryPath", retryPath(request));
        return view;
    }

    @ExceptionHandler(LogoutRevocationException.class)
    public ModelAndView logoutUnavailable(LogoutRevocationException exception, HttpServletRequest request) {
        ModelAndView view = new ModelAndView("error/service-unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        view.addObject("serviceMessage", "The platform is temporarily unavailable. Try again shortly.");
        view.addObject("retryPath", retryPath(request));
        return view;
    }

    /**
     * The error page renders this as a "Try again" link, which is a GET. Replaying the URI of a failed POST that
     * way is not a retry: the cart mutations live on sub-paths that have no GET mapping, so the link answers with
     * a method or handler error instead of the page the shopper expected. Offer it only when the failed request
     * was itself a GET; the template falls back to the workspace root otherwise.
     */
    private static String retryPath(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod()) ? request.getRequestURI() : null;
    }

    @ExceptionHandler(RateLimitedException.class)
    public ModelAndView rateLimited(RateLimitedException exception, HttpServletResponse response) {
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()));
        ModelAndView view = new ModelAndView("error/rate-limited", HttpStatus.TOO_MANY_REQUESTS);
        view.addObject("retryAfterSeconds", exception.retryAfterSeconds());
        return view;
    }
}
