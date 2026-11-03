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
public class QueueController {

    private final QueueManager queueManager;

    public QueueController(QueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse join(@Valid @RequestBody JoinQueueRequest request) {
        // Kiosk sends REGULAR or PRIORITY; QueueManager handles numbering and ordering.
        return TicketResponse.from(queueManager.addTicket(request.ticketType()));
    }

    @PostMapping("/next")
    public TicketResponse next() {
        // Staff uses this endpoint to remove the next ticket from the PriorityQueue.
        return queueManager.pollNext()
                .map(TicketResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NO_CONTENT, "There are no waiting tickets"));
    }

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

    @PostMapping("/skip")
    public TicketResponse skip() {
        return queueManager.skipCurrent()
                .map(TicketResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NO_CONTENT, "There is no active ticket"));
    }

    @PostMapping("/recall")
    public TicketResponse recall() {
        return queueManager.recallCurrent()
                .map(TicketResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NO_CONTENT, "There is no active ticket"));
    }

    @GetMapping("/history")
    public List<TicketResponse> history() {
        return queueManager.getHistory().stream().map(TicketResponse::from).toList();
    }

    public record JoinQueueRequest(@NotNull TicketType ticketType) {
    }

    public record TicketResponse(
            UUID id,
            String displayCode,
            TicketType ticketType,
            String status,
            LocalDateTime createdAt,
            LocalDateTime servedAt) {

        static TicketResponse from(Ticket ticket) {
            return new TicketResponse(
                    ticket.getId(),
                    displayCode(ticket),
                    ticket.getTicketType(),
                    ticket.getStatus().name(),
                    ticket.getCreatedAt(),
                    ticket.getServedAt());
        }

        static TicketResponse fromNullable(Ticket ticket) {
            return ticket == null ? null : from(ticket);
        }

        private static String displayCode(Ticket ticket) {
            // Display code is easier to read than the UUID: R-001 or P-001.
            String prefix = ticket.getTicketType() == TicketType.PRIORITY ? "P" : "R";
            return prefix + "-" + String.format("%03d", ticket.getQueueNumber());
        }
    }

    public record QueueStatusResponse(
            TicketResponse current,
            int waitingCount,
            List<TicketResponse> waiting,
            java.util.Map<TicketType, Long> waitingByType) {
    }
}
