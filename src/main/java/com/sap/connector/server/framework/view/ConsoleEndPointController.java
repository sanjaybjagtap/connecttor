package com.sap.connector.server.framework.view;


import com.sap.connector.server.framework.service.beans.TransactionStatusBean;
import com.sap.connector.server.framework.service.interfaces.DiagnosticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;
import java.util.*;

@Controller
public class ConsoleEndPointController {

    @Autowired
    DiagnosticsService diagnosticsService;

    /**
     * Get transaction statuses
     * @return
     */
    @GetMapping("/console_wp/txnStatus")
    @ResponseBody
    public Map<String,TransactionStatusBean> getStatusBeanMap() {
        return diagnosticsService.getStatusBeanMap();
    }

    /**
     * List pending transactions
     * @return
     */
    @GetMapping("/console_wp/pendingTxn")
    @ResponseBody
    public List<TransactionStatusBean> getPendingTxn() {
        return diagnosticsService.getPendingTxn();
    }

    /**
     * List available events
     * @return
     */
    @GetMapping("/console_wp/availableEvents")
    @ResponseBody
    public List<String> getEvents() {
        return diagnosticsService.getEvents();
    }

    /**
     * Return server information such as db pool size and thread count
     * @return
     * @throws SQLException
     */
    @GetMapping("/console_wp/serverInfo")
    @ResponseBody
    public Map<String,Object> getServerInfo() {
        return diagnosticsService.getServerInfo();
    }

    /**
     * Returns a list of active clients and related info
     * @return
     * @throws SQLException
     */
    @GetMapping("/console_wp/clientList")
    @ResponseBody
    public Map<String,Integer> getClientList() {
        return diagnosticsService.getClientList();
    }

    /**
     * Base functionality endpoints
     */

    /**
     * Logout an active console session
     * @param request
     * @param response
     * @return
     */
    @GetMapping(value="/console_wp/logout")
    public RedirectView logoutPage (HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null){
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        return index();
    }

    /**
     * Redirect to console home page after login or if accessed at root context
     * @return
     */
    @RequestMapping({"/","/console"})
    @ResponseStatus(HttpStatus.FOUND)
    public RedirectView index () {
        RedirectView redirectView = new RedirectView();
        redirectView.setUrl("/console/index.html");
        return redirectView;
    }
}
