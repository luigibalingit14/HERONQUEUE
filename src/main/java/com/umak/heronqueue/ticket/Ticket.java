package com.umak.heronqueue.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ============================================================================
 * TICKET ENTITY (NODE / ELEMENT NG DATA STRUCTURE)
 * ============================================================================
 *
 * KONSEPTO SA DATA STRUCTURES:
 * Ito ang nagsisilbing "Node" o data object na iniimbak sa ating Binary Heap.
 * Naglalaman ito ng:
 * 1. ticketType: 'PRIORITY' o 'REGULAR' (Ginamit ng Comparator para sa ranking)
 * 2. queueNumber: 1, 2, 3... (Ginamit ng Comparator para sa FIFO order sa parehong category)
 * 3. id: Unique UUID para sa identity
 * 4. status: 'WAITING', 'SERVING', 'COMPLETED', 'NO_SHOW'
 * 5. timestamps: createdAt at servedAt
 */
@Entity
@Table(name = "tickets")
public class Ticket {

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

    /**
     * CONSTRUCTOR:
     * Lumilikha ng bagong ticket na may status na 'WAITING' sa O(1) time.
     */
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

    /**
     * State transition: WAITING -> SERVING (O(1))
     */
    public void startServing(LocalDateTime timestamp) {
        this.status = TicketStatus.SERVING;
        this.servedAt = timestamp;
    }

    /**
     * State transition: SERVING -> COMPLETED (O(1))
     */
    public void complete() {
        this.status = TicketStatus.COMPLETED;
    }

    /**
     * State transition: SERVING -> NO_SHOW (O(1))
     */
    public void markNoShow() {
        this.status = TicketStatus.NO_SHOW;
    }
}
