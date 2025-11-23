package com.ignis.prestamil.service;

import org.springframework.stereotype.Service;

@Service
public class PrinterService {

    public void printTicket(String ticket) {
        System.out.println(ticket);
    }

}
