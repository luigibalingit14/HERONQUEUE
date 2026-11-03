package com.umak.heronqueue.queue;

import com.umak.heronqueue.ticket.Ticket;
import com.umak.heronqueue.ticket.TicketType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * ============================================================================
 * QUEUE REST CONTROLLER (WEB API LAYER)
 * ============================================================================
 *
 * PAPEL SA SYSTEM:
 * Ito ang tulay (bridge) sa pagitan ng web browser (Kiosk, Monitor, Staff)
 * at ng ating Java Data Structure sa backend.
 *
 * PAANO ITO GUMAGANA:
 * 1. Ang browser (JavaScript fetch) ay nagpapadala ng HTTP request (POST/GET) sa /api/queue/...
 * 2. Tinatanggap ng Controller ang request, tinitingnan kung may spam, at ipinapasa sa QueueManager.
 * 3. Ibinabalik ng Controller ang data bilang JSON pabalik sa user interface.
 */
@RestController
@RequestMapping("/api/queue")
@CrossOrigin(origins = "*")
public class QueueController {

    private final QueueManager queueManager;
    private final Map<String, Instant> lastJoinTimes = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastNextTimes = new ConcurrentHashMap<>();

    public QueueController(QueueManager queueManager) {
        this.queueManager = queueManager;
    }

    /**
     * ENDPOINT 1: POST /api/queue/join
     * Tinatawag ng Kiosk kapag pinindot ng estudyante ang "Regular" o "Priority".
     * May kasamang 3-second rate limit para maiwasan ang accidental spamming.
     */
    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse join(
            @Valid @RequestBody JoinQueueRequest request,
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            HttpServletRequest httpRequest) {
        String key = resolveClientKey(clientId, httpRequest);
        Instant now = Instant.now();
        Instant lastJoin = lastJoinTimes.get(key);
        if (lastJoin != null && Duration.between(lastJoin, now).toMillis() < 3000) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another ticket.");
        }
        lastJoinTimes.put(key, now);
        cleanOldEntries(lastJoinTimes, now.minusSeconds(60));

        return TicketResponse.from(queueManager.addTicket(request.ticketType()));
    }

    /**
     * ENDPOINT 2: POST /api/queue/next
     * Tinatawag ng Staff Dashboard kapag pinindot ang "Call next ticket".
     * Inaalis ang susunod na pinakamataas na priority ticket mula sa Binary Heap.
     */
    @PostMapping("/next")
    public TicketResponse next(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            HttpServletRequest httpRequest) {
        String key = resolveClientKey(clientId, httpRequest);
        Instant now = Instant.now();
        Instant lastNext = lastNextTimes.get(key);
        if (lastNext != null && Duration.between(lastNext, now).toMillis() < 1500) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Please wait before calling another ticket.");
        }
        lastNextTimes.put(key, now);
        cleanOldEntries(lastNextTimes, now.minusSeconds(60));

        return queueManager.pollNext()
                .map(TicketResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NO_CONTENT, "There are no waiting tickets"));
    }

    /**
     * ENDPOINT 3: GET /api/queue/status
     * Tinatawag ng Public Monitor, Kiosk, at Staff para sa real-time live display.
     * Mabilis itong sumasagot dahil binabasa lang nito ang snapshot sa Java RAM nang walang database overhead.
     */
    @GetMapping("/status")
    public QueueStatusResponse status() {
        QueueManager.QueueSnapshot snapshot = queueManager.getSnapshot();
        return new QueueStatusResponse(
                TicketResponse.fromNullable(snapshot.current()),
                snapshot.waitingCount(),
                snapshot.waiting().stream().map(TicketResponse::from).toList(),
                snapshot.waitingByType());
    }

    /**
     * ENDPOINT 4: POST /api/queue/skip
     * Minamarkahan ang kasalukuyang ticket bilang NO-SHOW.
     */
    @PostMapping("/skip")
    public TicketResponse skip() {
        return queueManager.skipCurrent()
                .map(TicketResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NO_CONTENT, "There is no active ticket"));
    }

    /**
     * ENDPOINT 5: POST /api/queue/recall
     * Muling ina-anunsyo ang kasalukuyang ticket.
     */
    @PostMapping("/recall")
    public TicketResponse recall() {
        return queueManager.recallCurrent()
                .map(TicketResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NO_CONTENT, "There is no active ticket"));
    }

    /**
     * ENDPOINT 6: GET /api/queue/history
     * Kinukuha ang kasaysayan ng mga napagserbisyuhan ngayong araw.
     */
    @GetMapping("/history")
    public List<TicketResponse> history() {
        return queueManager.getHistory().stream().map(TicketResponse::from).toList();
    }

    private String resolveClientKey(String clientId, HttpServletRequest httpRequest) {
        if (clientId != null && !clientId.isBlank()) {
            return clientId.trim();
        }
        if (httpRequest != null) {
            String forwarded = httpRequest.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return httpRequest.getRemoteAddr();
        }
        return "unknown";
    }

    private void cleanOldEntries(Map<String, Instant> map, Instant cutoff) {
        if (map.size() > 500) {
            map.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        }
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
