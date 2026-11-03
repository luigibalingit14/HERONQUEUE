package com.umak.heronqueue.queue;

import com.umak.heronqueue.ticket.Ticket;
import com.umak.heronqueue.ticket.TicketType;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Plain Java implementation of the queue algorithm used by HeronQueue.
 *
 * <p>PriorityQueue is a binary heap. It always keeps the next ticket at the
 * front, so adding and removing a ticket take O(log n) time.</p>
 */
public class TicketPriorityQueue {

    /*
     * The comparator is the queue rule:
     * 1. Priority tickets come first.
     * 2. Earlier numbers come first within the same ticket type.
     * 3. The UUID prevents two tickets from having the same order.
     */
    private static final Comparator<Ticket> TICKET_ORDER = Comparator
            .comparing(Ticket::getTicketType, Comparator.comparingInt(TicketPriorityQueue::priorityRank))
            .thenComparingInt(Ticket::getQueueNumber)
            .thenComparing(Ticket::getId);

    private final PriorityQueue<Ticket> tickets = new PriorityQueue<>(TICKET_ORDER);

    /**
     * Converts business priority into the comparator's ascending rank.
     *
     * <p>Priority receives rank 0 and Regular receives rank 1, so Java's
     * min-oriented heap removes Priority tickets first. TIME COMPLEXITY: O(1).</p>
     */
    private static int priorityRank(TicketType type) {
        return type == TicketType.PRIORITY ? 0 : 1;
    }

    /**
     * Inserts a ticket into the binary heap.
     *
     * <p>TIME COMPLEXITY: O(log n) because the new element may move upward to
     * restore the heap property. SPACE COMPLEXITY: O(1) auxiliary, O(n) heap.</p>
     */
    public void add(Ticket ticket) {
        tickets.offer(ticket);
    }

    /**
     * Removes and returns the highest-priority waiting ticket.
     *
     * <p>TIME COMPLEXITY: O(log n) because the replacement root may move
     * downward after removal; returns {@code null} when the heap is empty.</p>
     */
    public Ticket removeNext() {
        return tickets.poll();
    }

    /**
     * Reports how many tickets are currently waiting.
     *
     * <p>TIME COMPLEXITY: O(1), as {@link PriorityQueue} maintains its size.</p>
     */
    public int size() {
        return tickets.size();
    }

    /**
     * Produces a sorted copy for clients that need the full waiting order.
     *
     * <p>Heap iteration itself is not sorted, so this stream performs an
     * O(n log n) sort without mutating the queue. SPACE COMPLEXITY: O(n).</p>
     */
    public List<Ticket> sortedTickets() {
        // Iterating a heap is not sorted, so sort a copy for display only.
        return tickets.stream().sorted(TICKET_ORDER).toList();
    }

    /**
     * Counts waiting tickets by enum type using an {@link EnumMap}.
     *
     * <p>TIME COMPLEXITY: O(n) for the two heap traversals; SPACE COMPLEXITY:
     * O(1) for the fixed set of ticket types.</p>
     */
    public Map<TicketType, Long> countByType() {
        Map<TicketType, Long> counts = new EnumMap<>(TicketType.class);
        counts.put(TicketType.REGULAR, tickets.stream()
                .filter(ticket -> ticket.getTicketType() == TicketType.REGULAR)
                .count());
        counts.put(TicketType.PRIORITY, tickets.stream()
                .filter(ticket -> ticket.getTicketType() == TicketType.PRIORITY)
                .count());
        return counts;
    }

    /**
     * Re-inserts persisted waiting tickets after a service restart.
     *
     * <p>For n restored tickets, repeated insertion costs O(n log n), with
     * O(n) space occupied by the resulting binary heap.</p>
     */
    public void restore(List<Ticket> waitingTickets) {
        waitingTickets.forEach(this::add);
    }
}
