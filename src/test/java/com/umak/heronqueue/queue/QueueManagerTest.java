package com.umak.heronqueue.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.umak.heronqueue.ticket.Ticket;
import com.umak.heronqueue.ticket.TicketRepository;
import com.umak.heronqueue.ticket.TicketStatus;
import com.umak.heronqueue.ticket.TicketType;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueueManagerTest {

    @Test
    void priorityTicketsAreServedBeforeRegularTickets() {
        TicketRepository repository = org.mockito.Mockito.mock(TicketRepository.class);
        when(repository.findByStatus(any(TicketStatus.class))).thenReturn(List.of());
        when(repository.findTopByTicketTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByQueueNumberDesc(
                any(TicketType.class), any(), any())).thenReturn(null);
        when(repository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        QueueManager manager = new QueueManager(repository);

        manager.addTicket(TicketType.REGULAR);
        Ticket priority = manager.addTicket(TicketType.PRIORITY);

        assertEquals(priority.getId(), manager.pollNext().orElseThrow().getId());
        assertEquals(1, priority.getQueueNumber());
    }

    @Test
    void plainJavaQueueUsesPriorityAndNumberRules() {
        TicketPriorityQueue queue = new TicketPriorityQueue();
        Ticket regular = new Ticket(TicketType.REGULAR, 1);
        Ticket secondPriority = new Ticket(TicketType.PRIORITY, 2);
        Ticket firstPriority = new Ticket(TicketType.PRIORITY, 1);

        queue.add(regular);
        queue.add(secondPriority);
        queue.add(firstPriority);

        assertEquals(firstPriority.getId(), queue.removeNext().getId());
        assertEquals(secondPriority.getId(), queue.removeNext().getId());
        assertEquals(regular.getId(), queue.removeNext().getId());
    }
}
