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

/**
 * ============================================================================
 * QUEUE MANAGER (SERVICE LAYER)
 * ============================================================================
 * 
 * PAPEL SA SYSTEM:
 * Ito ang "utak" ng HeronQueue na nag-uugnay sa:
 * 1. In-Memory Data Structure (TicketPriorityQueue / Binary Heap sa RAM)
 * 2. Persistent Database (Supabase PostgreSQL / JPA Repository sa Disk)
 * 3. Concurrency Control (ReentrantLock para sa Thread-Safety)
 *
 * BAKIT IN-MEMORY HEAP + DATABASE?
 * - In-Memory Heap: Napakabilis mag-add at mag-poll ng susunod na ticket (O(log n)),
 *   at mabilis magbasa ng snapshot ang mga monitor nang hindi bumabagal ang database.
 * - Database: Nagsisilbing permanent storage upang kapag nag-restart o nag-brownout
 *   ang server, muling makuha (restore) ang lahat ng mga nakapila nang walang nawawala.
 */
@Service
public class QueueManager {

    private final TicketRepository ticketRepository;
    private final TicketPriorityQueue queue = new TicketPriorityQueue();

    // ReentrantLock: Sinisiguro na iisang thread lang ang nagbabago ng pila
    // sa bawat saglit upang maiwasan ang race condition / duplicate queue numbers.
    private final ReentrantLock queueLock = new ReentrantLock();

    // Ang kasalukuyang ticket na nasa counter (Now Serving).
    private Ticket currentTicket;

    /**
     * CONSTRUCTOR & RESTORATION:
     * Pagka-start ng Spring Boot, kinukuha ang lahat ng ticket na may status na 'WAITING'
     * sa database at muling ipinapasok sa binary heap upang i-reconstruct ang pila.
     */
    public QueueManager(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
        queue.restore(ticketRepository.findByStatus(TicketStatus.WAITING));
        currentTicket = ticketRepository.findByStatus(TicketStatus.SERVING).stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * PAG-CREATE AT PAG-ENQUEUE NG BAGONG TICKET:
     * 1. Ni-la-lock ang thread (ReentrantLock).
     * 2. Binabasa ang pinakahuling queue number ngayong araw (R-001 o P-001).
     * 3. Ini-increment ang number (+1), o nagre-reset sa 1 kung bagong araw.
     * 4. Sine-save sa PostgreSQL database (permanent record).
     * 5. Ipinapasok sa Binary Heap gamit ang queue.add() -> O(log n).
     */
    public Ticket addTicket(TicketType ticketType) {
        queueLock.lock();
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();

            // Hanapin ang pinakamalaking queue number ngayong araw para sa specific category
            Ticket lastTicket = ticketRepository
                    .findTopByTicketTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByQueueNumberDesc(
                            ticketType, startOfDay, startOfTomorrow);

            int nextQueueNumber = lastTicket == null ? 1 : lastTicket.getQueueNumber() + 1;

            // I-persist muna sa DB bago i-enqueue sa binary heap
            Ticket ticket = ticketRepository.save(new Ticket(ticketType, nextQueueNumber));
            queue.add(ticket);
            return ticket;
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * PAGTAWAG SA SUSUNOD NA TICKET (CALL NEXT):
     * 1. Kung may kasalukuyang sineserbisyuhan (currentTicket), iminamarka itong 'COMPLETED'.
     * 2. Tinatawag ang queue.removeNext() na nag-aalis sa root ng Binary Heap -> O(log n).
     * 3. Ang tinanggal na ticket ang nagiging bagong 'currentTicket' na may status na 'SERVING'.
     * 4. Kung wala nang tao sa heap, nagiging null ang currentTicket.
     */
    public Optional<Ticket> pollNext() {
        queueLock.lock();
        try {
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
     * NO-SHOW / SKIP TICKET:
     * Minamarkahan ang kasalukuyang ticket bilang 'NO_SHOW' kapag hindi sumipot
     * ang estudyante pagkatapos tawagin.
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
     * RECALL CURRENT TICKET:
     * Binabasa ang kasalukuyang aktibong ticket para muling i-anunsyo ng audio nang hindi binabago ang pila.
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
     * SERVING HISTORY:
     * Kumukuha ng listahan ng mga natapos (COMPLETED) at hindi sumipot (NO_SHOW) ngayong araw mula sa database.
     */
    public List<Ticket> getHistory() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return ticketRepository.findByStatusInAndServedAtGreaterThanEqualOrderByServedAtDesc(
                List.of(TicketStatus.COMPLETED, TicketStatus.NO_SHOW), start);
    }

    /**
     * LIVE QUEUE SNAPSHOT:
     * Kinukuha ang pinagsamang estado para sa mga monitor at kiosk:
     * - Sino ang kasalukuyang sineserbisyuhan
     * - Ilan ang naghihintay
     * - Listahan ng susunod na 5 tao sa pila (mula sa heap sorted copy)
     * - Bilang ng Regular vs Priority
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
     * Snapshot record na ipinapadala sa mga client sa pamamagitan ng JSON.
     */
    public record QueueSnapshot(
            Ticket current, int waitingCount, List<Ticket> waiting, Map<TicketType, Long> waitingByType) {
    }
}
