package com.umak.heronqueue.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {

    // UUID is the internal database identifier; queueNumber is the human-readable number.
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", nullable = false, length = 16)
    private TicketType ticketType;

    @Column(name = "queue_number", nullable = false)
    private int queueNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "served_at")
    private LocalDateTime servedAt;

    protected Ticket() {
    }

    public Ticket(TicketType ticketType, int queueNumber) {
        this.id = UUID.randomUUID();
        this.ticketType = ticketType;
        this.queueNumber = queueNumber;
        this.status = TicketStatus.WAITING;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public TicketType getTicketType() {
        return ticketType;
    }

    public int getQueueNumber() {
        return queueNumber;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getServedAt() {
        return servedAt;
    }

    public void startServing(LocalDateTime timestamp) {
        // A ticket changes from WAITING to SERVING when staff calls it.
        this.status = TicketStatus.SERVING;
        this.servedAt = timestamp;
    }

    public void complete() {
        // The current ticket is marked completed before another ticket is called.
        this.status = TicketStatus.COMPLETED;
    }

    public void markNoShow() {
        this.status = TicketStatus.NO_SHOW;
    }
}
