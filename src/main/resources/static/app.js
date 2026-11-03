const API_BASE = '/api/queue';

// Displays short-lived feedback messages without interrupting the user.
function showToast(message) {
  const toast = document.querySelector('#toast');
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('visible');
  window.setTimeout(() => toast.classList.remove('visible'), 3500);
}

// Central API helper used by kiosk, staff, and monitor pages.
async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Request failed (${response.status})`);
  }
  return response.status === 204 ? null : response.json();
}

async function joinQueue(ticketType) {
  try {
    const ticket = await request('/join', {
      method: 'POST',
      body: JSON.stringify({ ticketType })
    });
    document.querySelector('#ticketCode').textContent = ticket.displayCode;
    document.querySelector('#ticketType').textContent = `${ticket.ticketType} QUEUE`;
    // Find this ticket in the sorted waiting list to calculate its position.
    const status = await request('/status');
    const ahead = status.waiting.findIndex((item) => item.id === ticket.id);
    document.querySelector('#peopleAhead').textContent = Math.max(ahead, 0);
    showToast('Your place is secured. Please watch the display.');
  } catch (error) {
    showToast('Unable to join the queue right now. Please try again.');
    console.error(error);
  }
}

async function refreshStaff() {
  try {
    const status = await request('/status');
    document.querySelector('#waitingCount').textContent = status.waitingCount;
    document.querySelector('#nowServing').textContent = status.current?.displayCode || '—';
    document.querySelector('#priorityCount').textContent = status.waitingByType.PRIORITY || 0;
    document.querySelector('#regularCount').textContent = status.waitingByType.REGULAR || 0;
    const next = document.querySelector('#nextTickets');
    next.replaceChildren();
    status.waiting.slice(0, 5).forEach((ticket, index) => {
      const item = document.createElement('div');
      item.className = 'next-item';
      item.innerHTML = `<span>${index + 1}</span><strong>${ticket.displayCode}</strong><small>${ticket.ticketType}</small>`;
      next.appendChild(item);
    });
    if (!status.waiting.length) next.innerHTML = '<span class="empty-state">No waiting tickets.</span>';
    await refreshHistory();
  } catch (error) {
    showToast('Live queue data is unavailable.');
    console.error(error);
  }
}

async function staffAction(path, successMessage) {
  try {
    const ticket = await request(path, { method: 'POST' });
    showToast(`${ticket.displayCode} ${successMessage}`);
    await refreshStaff();
  } catch (error) {
    showToast('There is no active ticket for this action.');
    console.error(error);
  }
}

async function callNext() {
  try {
    await request('/next', { method: 'POST' });
    announceTicket((await request('/status')).current?.displayCode);
    await refreshStaff();
    showToast('Next ticket called.');
  } catch (error) {
    showToast('There are no waiting tickets to call.');
    console.error(error);
  }

  function announceTicket(displayCode) {
    if (!displayCode || !('speechSynthesis' in window)) return;
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(new SpeechSynthesisUtterance(`Now serving ${displayCode}.`));
  }

  async function refreshHistory() {
    const history = await request('/history');
    const list = document.querySelector('#historyList');
    if (!list) return;
    const query = document.querySelector('#historySearch')?.value.trim().toUpperCase() || '';
    list.replaceChildren();
    history.filter((ticket) => ticket.displayCode.includes(query)).slice(0, 10).forEach((ticket) => {
      const item = document.createElement('div');
      item.className = 'history-item';
      const time = ticket.servedAt ? new Date(ticket.servedAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) : '—';
      item.innerHTML = `<strong>${ticket.displayCode}</strong><span>${ticket.status === 'NO_SHOW' ? 'No-show' : 'Completed'}</span><time>${time}</time>`;
      list.appendChild(item);
    });
    if (!list.children.length) list.innerHTML = '<span class="empty-state">No matching tickets.</span>';
  }
}

function updateMonitorClock() {
  const clock = document.querySelector('#monitorClock');
  if (clock) {
    clock.textContent = new Intl.DateTimeFormat('en-PH', {
      hour: 'numeric',
      minute: '2-digit',
      hour12: true
    }).format(new Date());
  }
}

async function refreshMonitor() {
  try {
    const status = await request('/status');
    document.querySelector('#monitorCurrent').textContent =
      status.current?.displayCode || '—';
    document.querySelector('#monitorWaitingCount').textContent = status.waitingCount;

    const queue = document.querySelector('#monitorQueue');
    queue.replaceChildren();
    if (!status.waiting.length) {
      queue.innerHTML = '<div class="monitor-empty">No waiting tickets.</div>';
      return;
    }
    // Build the monitor list from the server's current priority order.
    status.waiting.forEach((ticket, index) => {
      const item = document.createElement('div');
      item.className = 'monitor-queue-item';
      item.innerHTML = `<span>${index + 1}</span><strong>${ticket.displayCode}</strong><small>${ticket.ticketType} queue</small>`;
      queue.appendChild(item);
    });
  } catch (error) {
    document.querySelector('#monitorStatus').textContent = 'Offline';
    showToast('Live queue data is unavailable.');
    console.error(error);
  }
}

document.querySelectorAll('[data-ticket-type]').forEach((button) => {
  button.addEventListener('click', () => joinQueue(button.dataset.ticketType));
});
document.querySelector('#callNext')?.addEventListener('click', callNext);
document.querySelector('#recallTicket')?.addEventListener('click', async () => {
  try {
    const ticket = await request('/recall', { method: 'POST' });
    announceTicket(ticket.displayCode);
    showToast(`${ticket.displayCode} recalled.`);
  } catch (error) {
    showToast('There is no active ticket to recall.');
  }
});
document.querySelector('#skipTicket')?.addEventListener('click', () => staffAction('/skip', 'marked as no-show.'));
document.querySelector('#historySearch')?.addEventListener('input', refreshHistory);
document.querySelector('#secretTrigger')?.addEventListener('click', () => {
  document.querySelector('#adminPanel').classList.toggle('visible');
});
if (document.querySelector('#waitingCount')) {
  refreshStaff();
  window.setInterval(refreshStaff, 10000);
}
if (document.querySelector('#monitorCurrent')) {
  updateMonitorClock();
  refreshMonitor();
  window.setInterval(updateMonitorClock, 1000);
  window.setInterval(refreshMonitor, 5000);
}
