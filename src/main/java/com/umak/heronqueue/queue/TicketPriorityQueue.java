package com.umak.heronqueue.queue;

import com.umak.heronqueue.ticket.Ticket;
import com.umak.heronqueue.ticket.TicketType;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * ============================================================================
 * DATA STRUCTURE IMPLEMENTATION: Priority Queue gamit ang Binary Min-Heap
 * Course: DATASTRU - Data Structures and Algorithms (University of Makati)
 * ============================================================================
 *
 * KONSEPTO NG DATA STRUCTURE:
 * - Ang PriorityQueue sa Java ay isang BINARY HEAP (specifically, a Min-Heap).
 * - Sa halip na purong FIFO (First-In, First-Out) tulad ng ordinaryong Queue,
 *   ang bawat elemento ay may kaukulang PRIORITY RANK.
 * - Ang may PINAKAMABABANG rank number (Min-Heap root) ang laging nasa unahan
 *   at unang lalabas kapag tinawag ang poll() / removeNext().
 *
 * ARRAY REPRESENTATION NG BINARY TREE SA LOOB NG HEAP:
 * Kahit mukhang tree ang binary heap, nakatago ito sa isang array internally:
 * - Parent index ng node i:       parent(i) = (i - 1) / 2
 * - Left child index ng node i:   left(i)   = (2 * i) + 1
 * - Right child index ng node i:  right(i)  = (2 * i) + 2
 *
 * TIME COMPLEXITY:
 * - Insert / Enqueue (add / offer):   O(log n)  -> Heapify Up (Shift Up)
 * - Remove Min / Dequeue (poll):      O(log n)  -> Heapify Down (Shift Down)
 * - Peek / Front element:             O(1)      -> Root element tickets[0]
 * - Size check:                       O(1)
 *
 * SPACE COMPLEXITY:
 * - O(n) kung saan ang n ay ang bilang ng mga aktibong naghihintay sa pila.
 */
public class TicketPriorityQueue {

    /**
     * COMPARATOR RULE (Batayan ng Priority):
     * 1. PRIORITY TYPE RULE: Inuuna ang PRIORITY (PWD, Senior, Pregnant) na may rank 0
     *    kaysa REGULAR na may rank 1.
     * 2. FIFO RULE: Kung pareho silang PRIORITY o pareho silang REGULAR,
     *    ang may mas mababang queue_number (unang dumating) ang mauuna.
     * 3. TIE-BREAKER: Kung magkapareho ng number at type, ginagamit ang unique UUID.
     */
    private static final Comparator<Ticket> TICKET_ORDER = Comparator
            .comparing(Ticket::getTicketType, Comparator.comparingInt(TicketPriorityQueue::priorityRank))
            .thenComparingInt(Ticket::getQueueNumber)
            .thenComparing(Ticket::getId);

    // Dito nakatago ang binary min-heap na sumusunod sa TICKET_ORDER rules sa itaas.
    private final PriorityQueue<Ticket> tickets = new PriorityQueue<>(TICKET_ORDER);

    /**
     * Binibigyan ng numeric rank ang bawat ticket category:
     * - PRIORITY (PWD, Seniors, Pregnant) = 0 (Mas mababang value = mas mataas na priority sa Min-Heap)
     * - REGULAR (Standard service lane)   = 1
     * Time Complexity: O(1)
     */
    private static int priorityRank(TicketType type) {
        return type == TicketType.PRIORITY ? 0 : 1;
    }

    /**
     * ENQUEUE OPERATION:
     * Nagdaragdag ng bagong ticket sa Binary Heap.
     *
     * Paano ito gumagana sa ilalim (Heapify Up):
     * 1. Inilalagay ang bagong ticket sa dulo ng heap array.
     * 2. Ikinukumpara ito sa kanyang parent node gamit ang formula: (index - 1) / 2.
     * 3. Kung mas mataas ang priority ng bagong ticket kaysa sa parent, nagpapalitan sila (swap).
     * 4. Uulitin ito pataas hanggang sa maabot ang tamang posisyon sa tree.
     *
     * Time Complexity: O(log n)
     */
    public void add(Ticket ticket) {
        tickets.offer(ticket);
    }

    /**
     * DEQUEUE OPERATION:
     * Kinukuha at inaalis ang ticket na nasa unahan (root ng Min-Heap)
     * na may pinakamataas na priority.
     *
     * Paano ito gumagana sa ilalim (Heapify Down):
     * 1. Kinukuha ang root element (tickets[0]) bilang return value.
     * 2. Inililipat ang pinakahuling elemento sa dulo ng array papunta sa root.
     * 3. Ikinukumpara ito sa kanyang left at right children: 2*i + 1 at 2*i + 2.
     * 4. Kung mas mababa ang priority nito kaysa sa pinakamaliit na child, nagpapalitan sila (swap).
     * 5. Uulitin ito pababa hanggang ma-restore ang min-heap property.
     *
     * Time Complexity: O(log n)
     * Returns: Ang susunod na ticket na dapat tawagin, o null kung walang nakapila.
     */
    public Ticket removeNext() {
        return tickets.poll();
    }

    /**
     * PEEK / SIZE CHECK:
     * Ibinabalik kung ilang tickets ang kasalukuyang naghihintay sa pila.
     * Time Complexity: O(1)
     */
    public int size() {
        return tickets.size();
    }

    /**
     * DISPLAY ORDER CONVERSION:
     * Ang internal array ng binary heap ay partially ordered lamang (hindi naka-sort mula index 0 hanggang n).
     * Kaya para maipakita sa Monitor o Kiosk ang tamang pagkakasunod-sunod mula 1st hanggang 5th in line,
     * kino-convert natin ito sa isang sorted list gamit ang comparator.
     * Time Complexity: O(n log n)
     */
    public List<Ticket> sortedTickets() {
        return tickets.stream().sorted(TICKET_ORDER).toList();
    }

    /**
     * BREAKDOWN COUNTING:
     * Binibilang kung ilan ang Regular at ilan ang Priority tickets na nasa pila.
     * Time Complexity: O(n)
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
     * PERSISTENCE RESTORATION:
     * Kapag nag-restart ang server, ibinabalik nito sa heap ang lahat ng WAITING tickets
     * mula sa database para hindi mawala ang pila.
     * Time Complexity: O(n log n)
     */
    public void restore(List<Ticket> waitingTickets) {
        waitingTickets.forEach(this::add);
    }
}
