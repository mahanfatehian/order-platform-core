package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.exception.BackendClientException;
import com.orderprocessing.webui.exception.SessionExpiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class PageExceptionHandler {
    @ExceptionHandler(SessionExpiredException.class)
    public ModelAndView expired(SessionExpiredException exception, RedirectAttributes redirect) {
        redirect.addFlashAttribute("warning", "Your session expired. Please sign in again.");
        return new ModelAndView("redirect:/login");
    }

    @ExceptionHandler(BackendClientException.class)
    public ModelAndView backend(BackendClientException exception, HttpServletRequest request) {
        if (exception.getStatus().value() == 404) return new ModelAndView("error/404", HttpStatus.NOT_FOUND);
        if (exception.getStatus().value() == 403) return new ModelAndView("error/403", HttpStatus.FORBIDDEN);
        ModelAndView view = new ModelAndView("error/service-unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        view.addObject("serviceMessage", "A platform service could not complete this request. No changes were made.");
        view.addObject("retryPath", request.getRequestURI());
        return view;
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ModelAndView unavailable(ResourceAccessException exception, HttpServletRequest request) {
        ModelAndView view = new ModelAndView("error/service-unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        view.addObject("serviceMessage", "The platform is temporarily unavailable. Try again shortly.");
        view.addObject("retryPath", request.getRequestURI());
        return view;
    }
}
