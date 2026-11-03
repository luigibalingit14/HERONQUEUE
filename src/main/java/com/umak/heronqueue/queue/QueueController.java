package com.umak.heronqueue.queue;

import com.umak.heronqueue.ticket.Ticket;
import com.umak.heronqueue.ticket.TicketType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/queue")
@CrossOrigin(origins = "*")
/**
 * Exposes queue operations as HTTP endpoints for kiosk, staff, and monitor UIs.
 *
 * <p>DATA STRUCTURE: delegates ordering to the manager's priority queue instead
 * of storing queue state in the controller. TIME COMPLEXITY: endpoint mapping
 * is O(1), while response conversion is O(n) for list endpoints.</p>
 *
 * <p>Flow: tumatanggap ng HTTP request ang controller, ipinapasa ang trabaho sa
 * QueueManager, at ibinabalik ang resulta bilang JSON response.</p>
 */
public class QueueController {

    private final QueueManager queueManager;

    public QueueController(QueueManager queueManager) {
        this.queueManager = queueManager;
    }

    /**
     * Accepts a ticket request and returns its generated display information.
     *
     * <p>Validation rejects a missing enum value before the manager inserts the
     * ticket. Queue insertion is O(log n), excluding persistence work.</p>
     */
    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse join(@Valid @RequestBody JoinQueueRequest request) {
        // Kiosk sends REGULAR or PRIORITY; QueueManager handles numbering and ordering.
        // @RequestBody ang nagko-convert ng JSON mula sa browser papunta sa Java record.
        return TicketResponse.from(queueManager.addTicket(request.ticketType()));
    }

    /**
     * Calls the next ticket selected by the priority-queue comparator.
     *
     * <p>Heap removal is O(log n); an empty queue is represented by HTTP 204
     * through a {@link ResponseStatusException}.</p>
     */
    @PostMapping("/next")
    public TicketResponse next() {
        // Staff uses this endpoint to remove the next ticket from the PriorityQueue.
        return queueManager.pollNext()
                .map(TicketResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NO_CONTENT, "There are no waiting tickets"));
    }

    /**
     * Returns one consistent snapshot for all live queue displays.
     *
     * <p>The manager creates the snapshot under its lock; mapping waiting
     * tickets to DTOs is O(n), where n is the number of waiting tickets.</p>
     */
    @GetMapping("/status")
    public QueueStatusResponse status() {
        // Staff, kiosk, and monitor all use one snapshot endpoint for live updates.
        QueueManager.QueueSnapshot snapshot = queueManager.getSnapshot();
        return new QueueStatusResponse(
                TicketResponse.fromNullable(snapshot.current()),
                snapshot.waitingCount(),
                snapshot.waiting().stream().map(TicketResponse::from).toList(),
                snapshot.waitingByType());
    }

    /**
     * Marks the active ticket as a no-show.
     *
     * <p>The manager performs an O(1) state transition, excluding the database
     * update, and returns 204 when no ticket is currently serving.</p>
     */
    @PostMapping("/skip")
    public TicketResponse skip() {
        return queueManager.skipCurrent()
                .map(TicketResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NO_CONTENT, "There is no active ticket"));
    }

    /**
     * Reads the active ticket without changing queue state.
     *
     * <p>The manager's lookup is O(1), and an absent active ticket produces
     * HTTP 204 rather than a fabricated response.</p>
     */
    @PostMapping("/recall")
    public TicketResponse recall() {
        return queueManager.recallCurrent()
                .map(TicketResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NO_CONTENT, "There is no active ticket"));
    }

    /**
     * Returns today's completed and no-show tickets for staff history.
     *
     * <p>Mapping the repository result to response records is O(h), where h is
     * the number of returned history entries.</p>
     */
    @GetMapping("/history")
    public List<TicketResponse> history() {
        return queueManager.getHistory().stream().map(TicketResponse::from).toList();
    }

    /**
     * Validated request DTO containing the requested queue category.
     *
     * <p>The enum provides a closed set of polymorphic ticket types rather than
     * relying on unchecked strings.</p>
     */
    public record JoinQueueRequest(@NotNull TicketType ticketType) {
    }

    /**
     * Immutable API DTO that prevents persistence entities from leaking outward.
     *
     * <p>Conversion is O(1) per ticket. The UUID is retained for identity while
     * displayCode exposes the readable queue number.</p>
     */
    public record TicketResponse(
            UUID id,
            String displayCode,
            TicketType ticketType,
            String status,
            LocalDateTime createdAt,
            LocalDateTime servedAt) {

        /**
         * Converts one domain entity to an HTTP response record in O(1) time.
         */
        static TicketResponse from(Ticket ticket) {
            return new TicketResponse(
                    ticket.getId(),
                    displayCode(ticket),
                    ticket.getTicketType(),
                    ticket.getStatus().name(),
                    ticket.getCreatedAt(),
                    ticket.getServedAt());
        }

        /**
         * Converts an optional active entity while preserving a null API value.
         *
         * <p>TIME COMPLEXITY: O(1).</p>
         */
        static TicketResponse fromNullable(Ticket ticket) {
            return ticket == null ? null : from(ticket);
        }

        /**
         * Builds a user-facing prefix and zero-padded queue number.
         *
         * <p>TIME COMPLEXITY: O(1); UUID remains an internal unique identifier,
         * while the code is optimized for human reading and announcements.</p>
         */
        private static String displayCode(Ticket ticket) {
            // Display code is easier to read than the UUID: R-001 or P-001.
            String prefix = ticket.getTicketType() == TicketType.PRIORITY ? "P" : "R";
            return prefix + "-" + String.format("%03d", ticket.getQueueNumber());
        }
    }

    /**
     * Immutable aggregate sent to live queue displays.
     *
     * <p>It contains the current ticket, waiting count, ordered waiting list,
     * and per-type counts prepared by {@link QueueManager}.</p>
     */
    public record QueueStatusResponse(
            TicketResponse current,
            int waitingCount,
            List<TicketResponse> waiting,
            java.util.Map<TicketType, Long> waitingByType) {
    }
}
