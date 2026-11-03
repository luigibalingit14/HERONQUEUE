package com.umak.heronqueue.ticket;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence abstraction for {@link Ticket} entities.
 *
 * <p>DATA STRUCTURE: the database stores tickets as indexed rows; Spring Data
 * derives queries from method names. TIME COMPLEXITY is database-dependent,
 * typically improved by indexes on status, type, and timestamp columns.</p>
 */
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    /**
     * Finds tickets in one workflow state, used to rebuild the in-memory heap.
     *
     * <p>Returns O(n) rows in the result, where n is the matching ticket count;
     * the database performs the filtering.</p>
     */
    List<Ticket> findByStatus(TicketStatus status);

    /**
     * Finds the latest daily queue number for one ticket type.
     *
     * <p>The inclusive/exclusive time range represents one calendar day, so
     * historical rows do not affect today's numbering. Ordering selects one
     * maximum queue number; query cost depends on database indexes.</p>
     */
    Ticket findTopByTicketTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByQueueNumberDesc(
            TicketType ticketType, LocalDateTime start, LocalDateTime end);

    /**
     * Retrieves completed or no-show tickets served from the given time onward.
     *
     * <p>Results are ordered newest first by the database; conversion to a Java
     * list is O(h), where h is the number of returned history rows.</p>
     */
    List<Ticket> findByStatusInAndServedAtGreaterThanEqualOrderByServedAtDesc(
            Collection<TicketStatus> statuses, LocalDateTime start);
}
