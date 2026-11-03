package com.umak.heronqueue.ticket;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByStatus(TicketStatus status);

    Ticket findTopByTicketTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByQueueNumberDesc(
            TicketType ticketType, LocalDateTime start, LocalDateTime end);

    List<Ticket> findByStatusInAndServedAtGreaterThanEqualOrderByServedAtDesc(
            Collection<TicketStatus> statuses, LocalDateTime start);
}
