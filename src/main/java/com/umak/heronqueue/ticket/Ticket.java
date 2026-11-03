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
/**
 * JPA entity representing one student-service queue ticket.
 *
 * <p>OOP: private fields enforce encapsulation, while enums provide a type-safe
 * polymorphic choice of ticket type and status. TIME COMPLEXITY: in-memory
 * construction and state changes are O(1); persistence cost is database-dependent.</p>
 */
public class Ticket {

    // UUID is the internal database identifier; queueNumber is the human-readable number.
    // Encapsulation keeps identity and workflow state controlled by this entity.
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
     * Creates a new waiting ticket with a unique internal identity.
     *
     * <p>TIME COMPLEXITY: O(1). The queue number is supplied by the manager,
     * which owns daily numbering, while this entity owns initial state.</p>
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
     * Transitions this ticket from WAITING to SERVING.
     *
     * <p>TIME COMPLEXITY: O(1). Encapsulation places the valid state mutation
     * inside the entity instead of exposing writable status fields.</p>
     */
    public void startServing(LocalDateTime timestamp) {
        // A ticket changes from WAITING to SERVING when staff calls it;
        // ang QueueManager ang nagse-save ng bagong status sa database.
        this.status = TicketStatus.SERVING;
        this.servedAt = timestamp;
    }

    /**
     * Transitions the active ticket to COMPLETED.
     *
     * <p>TIME COMPLEXITY: O(1); the database save is performed by the service
     * layer after this in-memory state change.</p>
     */
    public void complete() {
        // The current ticket is marked completed before another ticket is called.
        this.status = TicketStatus.COMPLETED;
    }

    /**
     * Transitions the active ticket to NO_SHOW.
     *
     * <p>TIME COMPLEXITY: O(1), with the service layer responsible for
     * persisting the updated entity.</p>
     */
    public void markNoShow() {
        this.status = TicketStatus.NO_SHOW;
    }
}
