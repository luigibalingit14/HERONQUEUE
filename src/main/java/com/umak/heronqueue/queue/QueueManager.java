package com.umak.heronqueue.queue;

import com.umak.heronqueue.ticket.Ticket;
import com.umak.heronqueue.ticket.TicketRepository;
import com.umak.heronqueue.ticket.TicketStatus;
import com.umak.heronqueue.ticket.TicketType;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class QueueManager {

    private final TicketRepository ticketRepository;
    private final TicketPriorityQueue queue = new TicketPriorityQueue();
    private final ReentrantLock queueLock = new ReentrantLock();
    private Ticket currentTicket;

    public QueueManager(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
        // Rebuild the in-memory heap after a restart using waiting database records.
        queue.restore(ticketRepository.findByStatus(TicketStatus.WAITING));
        currentTicket = ticketRepository.findByStatus(TicketStatus.SERVING).stream()
                .findFirst()
                .orElse(null);
    }

    public Ticket addTicket(TicketType ticketType) {
        queueLock.lock();
        try {
            // Numbering is independent for Regular and Priority queues and resets daily.
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();
            Ticket lastTicket = ticketRepository
                    .findTopByTicketTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByQueueNumberDesc(
                            ticketType, startOfDay, startOfTomorrow);
            int nextQueueNumber = lastTicket == null ? 1 : lastTicket.getQueueNumber() + 1;
            Ticket ticket = ticketRepository.save(new Ticket(ticketType, nextQueueNumber));
            queue.add(ticket);
            return ticket;
        } finally {
            queueLock.unlock();
        }
    }

    public Optional<Ticket> pollNext() {
        queueLock.lock();
        try {
            // The previous ticket is completed before the next highest-priority ticket is served.
            if (currentTicket != null) {
                currentTicket.complete();
                ticketRepository.save(currentTicket);
            }

            Ticket next = queue.removeNext();
            if (next == null) {
                currentTicket = null;
                return Optional.empty();
            }

            next.startServing(LocalDateTime.now());
            currentTicket = ticketRepository.save(next);
            return Optional.of(currentTicket);
        } finally {
            queueLock.unlock();
        }
    }

    public Optional<Ticket> skipCurrent() {
        queueLock.lock();
        try {
            if (currentTicket == null) {
                return Optional.empty();
            }
            currentTicket.markNoShow();
            Ticket skipped = ticketRepository.save(currentTicket);
            currentTicket = null;
            return Optional.of(skipped);
        } finally {
            queueLock.unlock();
        }
    }

    public Optional<Ticket> recallCurrent() {
        queueLock.lock();
        try {
            return Optional.ofNullable(currentTicket);
        } finally {
            queueLock.unlock();
        }
    }

    public List<Ticket> getHistory() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return ticketRepository.findByStatusInAndServedAtGreaterThanEqualOrderByServedAtDesc(
                List.of(TicketStatus.COMPLETED, TicketStatus.NO_SHOW), start);
    }

    public QueueSnapshot getSnapshot() {
        queueLock.lock();
        try {
            return new QueueSnapshot(
                    currentTicket,
                    queue.size(),
                    queue.sortedTickets(),
                    queue.countByType());
        } finally {
            queueLock.unlock();
        }
    }

    public record QueueSnapshot(
            Ticket current, int waitingCount, List<Ticket> waiting, Map<TicketType, Long> waitingByType) {
    }
}
