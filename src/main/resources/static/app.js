const API_BASE = '/api/queue';

// Iisang API helper ang ginagamit ng kiosk, staff dashboard, at public monitor.

/**
 * Displays short-lived feedback without interrupting the current page.
 * DOM manipulation updates one existing toast element and its CSS state.
 */
function showToast(message) {
  const toast = document.querySelector('#toast');
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('visible');
  window.setTimeout(() => toast.classList.remove('visible'), 3500);
}

/**
 * Sends an HTTP request to the Spring Boot queue API.
 * async/await keeps network control flow readable; non-2xx responses become
 * rejected Promises so each caller can show page-specific feedback.
 */
async function request(path, options = {}) {
  // Ipadala ang request sa Spring Boot API; dito rin hinahawakan ang HTTP errors.
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    cache: 'no-store',
    ...options
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Request failed (${response.status})`);
  }
  return response.status === 204 ? null : response.json();
}

function startRefreshLoop(refresh, interval) {
  let timeoutId;
  let refreshing = false;

  const run = async () => {
    if (document.hidden || refreshing) return;
    refreshing = true;
    try {
      await refresh();
    } finally {
      refreshing = false;
      if (!document.hidden) timeoutId = window.setTimeout(run, interval);
    }
  };

  document.addEventListener('visibilitychange', () => {
    window.clearTimeout(timeoutId);
    if (!document.hidden) run();
  });
  run();
}

/**
 * Joins a selected queue, renders the generated ticket, and calculates
 * position from the server's priority-ordered waiting array.
 */
async function joinQueue(ticketType) {
  try {
    // Kumuha ng ticket sa server, saka ipakita ang code at pila sa kiosk.
    const ticket = await request('/join', {
      method: 'POST',
      body: JSON.stringify({ ticketType })
    });
    document.querySelector('#ticketCode').textContent = ticket.displayCode;
    document.querySelector('#ticketType').textContent = `${ticket.ticketType} QUEUE`;
    // The second awaited API call ensures the position uses current server data.
    const status = await request('/status');
    const ahead = status.waiting.findIndex((item) => item.id === ticket.id);
    document.querySelector('#peopleAhead').textContent = Math.max(ahead, 0);
    showToast('Your place is secured. Please watch the display.');
  } catch (error) {
    showToast('Unable to join the queue right now. Please try again.');
    console.error(error);
  }
}

async function refreshKiosk() {
  const nowServing = document.querySelector('#kioskNowServing');
  if (!nowServing) return;

  try {
    const status = await request('/status');
    nowServing.textContent = status.current?.displayCode || '—';
  } catch (error) {
    nowServing.textContent = 'Unavailable';
    console.error(error);
  }
}

/**
 * Refreshes staff counters, active ticket, next-ticket preview, and history.
 * DOM nodes are rebuilt from the API snapshot so the visible order matches the
 * backend PriorityQueue rather than a separately maintained client structure.
 */
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

/**
 * Executes a staff state-changing action and refreshes the staff dashboard.
 * The shared async helper avoids duplicating POST and error-handling logic.
 */
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

function announceTicket(displayCode) {
  if (!displayCode || !('speechSynthesis' in window)) return;
  // Kanselahin muna ang lumang announcement para hindi magsabay ang boses.
  window.speechSynthesis.cancel();
  window.speechSynthesis.speak(new SpeechSynthesisUtterance(`Now serving ${displayCode}.`));
}

async function refreshHistory() {
  const list = document.querySelector('#historyList');
  if (!list) return;

  try {
    // Ang history ay galing sa API; dito lang ito sini-search at ipinapakita.
    const history = await request('/history');
    const query = document.querySelector('#historySearch')?.value.trim().toUpperCase() || '';
    list.replaceChildren();
    history.filter((ticket) => ticket.displayCode.includes(query)).slice(0, 10).forEach((ticket) => {
      const item = document.createElement('div');
      item.className = 'history-item';
      const time = ticket.servedAt ? new Date(ticket.servedAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) : '—';
      item.innerHTML = `<strong>${ticket.displayCode}</strong><span>${ticket.status === 'NO_SHOW' ? 'No-show' : 'Completed'}</span><time>${time}</time>`;
      list.appendChild(item);
    });
    if (!list.children.length) {
      const emptyMessage = query ? 'No matching tickets.' : 'No completed tickets yet.';
      list.innerHTML = `<span class="empty-state">${emptyMessage}</span>`;
    }
  } catch (error) {
    showToast('Serving history is unavailable.');
    console.error(error);
  }
}

/**
 * Calls the next ticket, announces it, and reloads the staff view.
 * Multiple await expressions serialize dependent API calls: the status request
 * runs after the next-ticket operation has completed.
 */
async function callNext() {
  try {
    // Tinatawag muna ang next-ticket endpoint bago kunin ang bagong active ticket.
    await request('/next', { method: 'POST' });
    announceTicket((await request('/status')).current?.displayCode);
    await refreshStaff();
    showToast('Next ticket called.');
  } catch (error) {
    showToast('There are no waiting tickets to call.');
    console.error(error);
  }
}

/**
 * Updates the monitor clock using the Philippines locale and current time.
 * The caller schedules this function once per second with setInterval.
 */
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

/**
 * Refreshes the public monitor's current ticket and ordered waiting list.
 * Each refresh replaces the rendered children so stale tickets disappear.
 */
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
  // Ang button value ang nagsasabi kung REGULAR o PRIORITY ang ipapasok sa queue.
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
if (document.querySelector('#kioskNowServing')) {
  // Kiosk page lang: kunin agad ang active ticket at i-refresh kada 5 segundo.
  startRefreshLoop(refreshKiosk, 5000);
}
if (document.querySelector('#waitingCount')) {
  // Staff page lang: walang sabay-sabay na poll at titigil habang nakatago ang tab.
  startRefreshLoop(refreshStaff, 10000);
}
if (document.querySelector('#monitorCurrent')) {
  // Monitor page lang: i-update ang orasan at queue display.
  updateMonitorClock();
  window.setInterval(updateMonitorClock, 1000);
  startRefreshLoop(refreshMonitor, 5000);
}
