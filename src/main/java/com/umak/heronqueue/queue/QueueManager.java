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
/**
 * Coordinates persistent tickets with the in-memory priority queue.
 *
 * <p>DATA STRUCTURES: a {@link TicketPriorityQueue} binary heap plus repository
 * query results. TIME COMPLEXITY: heap insertion/removal is O(log n), while
 * snapshots and history depend on the number of waiting or stored tickets.</p>
 */
public class QueueManager {

    private final TicketRepository ticketRepository;
    private final TicketPriorityQueue queue = new TicketPriorityQueue();
    private final ReentrantLock queueLock = new ReentrantLock();
    private Ticket currentTicket;

    /**
     * Restores waiting tickets into the heap after application startup.
     *
     * <p>RESTORE COST: O(n log n) using repeated heap insertion; database query
     * cost depends on the persistence provider. The repository is injected to
     * keep storage concerns separate from queue-management logic.</p>
     */
    public QueueManager(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
        // Rebuild the in-memory heap after a restart using waiting database records.
        queue.restore(ticketRepository.findByStatus(TicketStatus.WAITING));
        currentTicket = ticketRepository.findByStatus(TicketStatus.SERVING).stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Creates and enqueues the next numbered ticket for a ticket type.
     *
     * <p>TIME COMPLEXITY: O(log n) for heap insertion, excluding the database
     * query/save. The date range makes numbering restart each day without
     * deleting historical records.</p>
     */
    public Ticket addTicket(TicketType ticketType) {
        // ReentrantLock makes the read-number/save/add sequence atomic, preventing
        // two concurrent requests from receiving the same queue number.
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

    /**
     * Completes the active ticket and serves the next heap-minimum ticket.
     *
     * <p>TIME COMPLEXITY: O(log n) for removal from the binary heap; peeking is
     * O(1). The returned Optional explicitly represents an empty queue.</p>
     */
    public Optional<Ticket> pollNext() {
        // The lock prevents staff requests from racing while currentTicket and
        // the heap are updated together.
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

    /**
     * Marks the currently serving ticket as a no-show.
     *
     * <p>TIME COMPLEXITY: O(1), excluding the database update. Locking keeps
     * skip behavior consistent with simultaneous next/recall requests.</p>
     */
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

    /**
     * Returns the active ticket without changing queue state.
     *
     * <p>TIME COMPLEXITY: O(1); the lock provides a consistent read of the
     * mutable current-ticket reference.</p>
     */
    public Optional<Ticket> recallCurrent() {
        queueLock.lock();
        try {
            return Optional.ofNullable(currentTicket);
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Retrieves today's completed and no-show tickets in reverse service order.
     *
     * <p>TIME COMPLEXITY: database-dependent, typically O(log n + h) with a
     * suitable index, where h is the number of returned history rows.</p>
     */
    public List<Ticket> getHistory() {
        // The LocalDate range starts at midnight, limiting history to today
        // while preserving older records for future reporting.
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return ticketRepository.findByStatusInAndServedAtGreaterThanEqualOrderByServedAtDesc(
                List.of(TicketStatus.COMPLETED, TicketStatus.NO_SHOW), start);
    }

    /**
     * Builds one consistent view for staff, kiosk, and monitor clients.
     *
     * <p>TIME COMPLEXITY: O(n log n) because the heap is copied and sorted for
     * display; counting ticket types is O(n). The lock prevents a partial view.</p>
     */
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

    /**
     * Immutable response model containing the active ticket and waiting metrics.
     *
     * <p>Using a record demonstrates encapsulation for a read-only snapshot while
     * the domain entity remains responsible for ticket state transitions.</p>
     */
    public record QueueSnapshot(
            Ticket current, int waitingCount, List<Ticket> waiting, Map<TicketType, Long> waitingByType) {
    }
}
