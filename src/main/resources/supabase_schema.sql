create extension if not exists pgcrypto;

create table if not exists tickets (
    id uuid primary key default gen_random_uuid(),
    ticket_type varchar(16) not null check (ticket_type in ('REGULAR', 'PRIORITY')),
    queue_number integer not null,
    status varchar(16) not null default 'WAITING'
        check (status in ('WAITING', 'SERVING', 'COMPLETED', 'NO_SHOW')),
    created_at timestamptz not null default now(),
    served_at timestamptz
);

create index if not exists idx_tickets_waiting_order
    on tickets (status, ticket_type, created_at);

alter table tickets add column if not exists queue_number integer;
