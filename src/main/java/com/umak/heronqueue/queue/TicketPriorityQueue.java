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

    private static int priorityRank(TicketType type) {
        return type == TicketType.PRIORITY ? 0 : 1;
    }

    public void add(Ticket ticket) {
        tickets.offer(ticket);
    }

    public Ticket removeNext() {
        return tickets.poll();
    }

    public int size() {
        return tickets.size();
    }

    public List<Ticket> sortedTickets() {
        // Iterating a heap is not sorted, so sort a copy for display only.
        return tickets.stream().sorted(TICKET_ORDER).toList();
    }

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

    public void restore(List<Ticket> waitingTickets) {
        waitingTickets.forEach(this::add);
    }
}
