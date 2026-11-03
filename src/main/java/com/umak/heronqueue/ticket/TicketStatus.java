package com.umak.heronqueue.ticket;

/**
 * Mga posibleng estado ng ticket habang dumadaan ito sa queue workflow.
 */
public enum TicketStatus {
    // Nasa heap at naghihintay matawag.
    WAITING,
    // Kasalukuyang tinatawag o inaasikaso ng staff.
    SERVING,
    // Natapos na ang serbisyo para sa ticket.
    COMPLETED,
    // Hindi sumagot o hindi dumating ang may hawak ng ticket.
    NO_SHOW
}
