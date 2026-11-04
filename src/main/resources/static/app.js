const API_BASE = '/api/queue';
let joiningQueue = false;
let callingNext = false;
let staffActionBusy = false;

// Iisang API helper ang ginagamit ng kiosk, staff dashboard, at public monitor.

/**
 * Generates or retrieves a persistent client ID for this browser.
 * Used by backend rate limiters to prevent accidental rapid submissions
 * from the same device without affecting other users on shared networks.
 */
function getClientId() {
  try {
    let id = localStorage.getItem('heronqueue_client_id');
    if (!id) {
      id = 'client_' + Math.random().toString(36).substring(2, 11) + '_' + Date.now().toString(36);
      localStorage.setItem('heronqueue_client_id', id);
    }
    return id;
  } catch (e) {
    return 'anonymous_client';
  }
}

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
  const headers = {
    'Content-Type': 'application/json',
    'X-Client-Id': getClientId(),
    ...(options.headers || {})
  };

  // Ipadala ang request sa Spring Boot API; dito rin hinahawakan ang HTTP errors.
  const response = await fetch(`${API_BASE}${path}`, {
    cache: 'no-store',
    ...options,
    headers
  });

  if (!response.ok) {
    const body = await response.text();
    let message = body;
    try {
      const parsed = JSON.parse(body);
      message = parsed.message || parsed.detail || body;
    } catch (_) {}

    const err = new Error(message || `Request failed (${response.status})`);
    err.status = response.status;
    throw err;
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
 * Includes immediate anti-spam locking and persistent cooldown protection.
 */
async function joinQueue(ticketType) {
  if (joiningQueue) return;
  joiningQueue = true;
  setQueueButtonsLoading(true);

  let ticketCreated = false;
  try {
    // Kumuha ng ticket sa server, saka ipakita ang code at pila sa kiosk.
    const ticket = await request('/join', {
      method: 'POST',
      body: JSON.stringify({ ticketType })
    });
    ticketCreated = true;

    const ticketCodeEl = document.querySelector('#ticketCode');
    const ticketTypeEl = document.querySelector('#ticketType');
    if (ticketCodeEl) ticketCodeEl.textContent = ticket.displayCode;
    if (ticketTypeEl) ticketTypeEl.textContent = `${ticket.ticketType} QUEUE`;

    const printTicket = document.querySelector('#printTicket');
    const receiptTicketCode = document.querySelector('#receiptTicketCode');
    const receiptQueueType = document.querySelector('#receiptQueueType');
    const receiptCreatedAt = document.querySelector('#receiptCreatedAt');
    const receiptPeopleAhead = document.querySelector('#receiptPeopleAhead');
    if (receiptTicketCode) receiptTicketCode.textContent = ticket.displayCode;
    if (receiptQueueType) receiptQueueType.textContent = `${ticket.ticketType} QUEUE`;
    if (receiptCreatedAt) {
      receiptCreatedAt.textContent = new Date(ticket.createdAt).toLocaleString('en-PH', {
        dateStyle: 'long',
        timeStyle: 'short'
      });
    }
    if (receiptPeopleAhead) receiptPeopleAhead.textContent = '-';
    if (printTicket) printTicket.hidden = false;

    // Highlight the ticket card visually to signal clear completion
    const ticketOutput = document.querySelector('.ticket-output');
    if (ticketOutput) {
      ticketOutput.classList.remove('issued');
      void ticketOutput.offsetWidth; // Trigger reflow to restart animation
      ticketOutput.classList.add('issued');
    }

    // The second awaited API call ensures the position uses current server data.
    const status = await request('/status');
    const ahead = status.waiting.findIndex((item) => item.id === ticket.id);
    const aheadEl = document.querySelector('#peopleAhead');
    const peopleAhead = Math.max(ahead, 0);
    if (aheadEl) aheadEl.textContent = peopleAhead;
    if (receiptPeopleAhead) receiptPeopleAhead.textContent = peopleAhead;

    showToast('Your place is secured. You can print or save your ticket as a PDF.');
  } catch (error) {
    if (error.status === 429) {
      showToast('Please wait a moment before requesting another ticket.');
    } else {
      showToast(ticketCreated
        ? 'Ticket created, but queue position could not be refreshed.'
        : 'Unable to join the queue right now. Please try again.');
    }
    console.error(error);
  } finally {
    if (ticketCreated) {
      await coolDownQueueButtons(6);
    } else {
      // Short delay on failure to avoid rapid button mashing
      await new Promise((resolve) => window.setTimeout(resolve, 2000));
      joiningQueue = false;
      setQueueButtonsLoading(false);
    }
  }
}

/**
 * Counts down and disables kiosk buttons, persisting across accidental page reloads.
 */
async function coolDownQueueButtons(seconds) {
  const buttons = [...document.querySelectorAll('[data-ticket-type]')];
  buttons.forEach((button) => {
    button.disabled = true;
    button.setAttribute('aria-busy', 'false');
  });

  const targetTime = Date.now() + seconds * 1000;
  try {
    sessionStorage.setItem('heronqueue_kiosk_cooldown_until', String(targetTime));
  } catch (_) {}

  while (true) {
    const remainingMs = targetTime - Date.now();
    const remainingSec = Math.ceil(remainingMs / 1000);

    if (remainingSec <= 0) {
      try {
        sessionStorage.removeItem('heronqueue_kiosk_cooldown_until');
      } catch (_) {}
      buttons.forEach((button) => {
        const label = button.querySelector('strong');
        if (label && button.dataset.readyLabel) {
          label.textContent = button.dataset.readyLabel;
        }
        button.disabled = false;
        button.setAttribute('aria-busy', 'false');
      });
      joiningQueue = false;
      break;
    }

    buttons.forEach((button) => {
      const label = button.querySelector('strong');
      if (label) label.textContent = `Please wait (${remainingSec}s)`;
    });

    await new Promise((resolve) => window.setTimeout(resolve, 500));
  }
}

function checkPersistentKioskCooldown() {
  try {
    const saved = sessionStorage.getItem('heronqueue_kiosk_cooldown_until');
    if (!saved) return;
    const remainingSec = Math.ceil((parseInt(saved, 10) - Date.now()) / 1000);
    if (remainingSec > 0) {
      joiningQueue = true;
      setQueueButtonsLoading(true);
      coolDownQueueButtons(remainingSec);
    } else {
      sessionStorage.removeItem('heronqueue_kiosk_cooldown_until');
    }
  } catch (_) {}
}

function setQueueButtonsLoading(isLoading) {
  document.querySelectorAll('[data-ticket-type]').forEach((button) => {
    const label = button.querySelector('strong');
    if (!label) return;

    button.dataset.readyLabel ??= label.textContent;
    button.disabled = isLoading;
    button.setAttribute('aria-busy', String(isLoading));
    label.textContent = isLoading ? 'Getting ticket...' : button.dataset.readyLabel;
  });
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

function setStaffControlsBusy(isBusy) {
  const callBtn = document.querySelector('#callNext');
  const recallBtn = document.querySelector('#recallTicket');
  const skipBtn = document.querySelector('#skipTicket');

  if (callBtn) callBtn.disabled = isBusy;
  if (recallBtn) recallBtn.disabled = isBusy;
  if (skipBtn) skipBtn.disabled = isBusy;
}

/**
 * Applies a visible cooldown to the Call Next button to prevent accidental spamming.
 */
async function coolDownCallNextButton(seconds) {
  const callBtn = document.querySelector('#callNext');
  if (!callBtn) return;

  callBtn.disabled = true;
  callBtn.classList.add('cooldown');

  for (let remaining = seconds; remaining > 0; remaining -= 1) {
    callBtn.innerHTML = `<span class="material-symbols-outlined">timer</span><span>Ready in ${remaining}s</span>`;
    await new Promise((resolve) => window.setTimeout(resolve, 1000));
  }

  callBtn.classList.remove('cooldown');
  if (callBtn.dataset.originalHtml) {
    callBtn.innerHTML = callBtn.dataset.originalHtml;
  }
}

/**
 * Executes a staff state-changing action and refreshes the staff dashboard.
 * Protects against rapid repeated clicks (no-show / skip).
 */
async function staffAction(path, successMessage) {
  if (callingNext || staffActionBusy) return;
  staffActionBusy = true;
  setStaffControlsBusy(true);

  try {
    const ticket = await request(path, { method: 'POST' });
    showToast(`${ticket.displayCode} ${successMessage}`);
    await refreshStaff();
    await new Promise((resolve) => window.setTimeout(resolve, 1500));
  } catch (error) {
    showToast('There is no active ticket for this action.');
    console.error(error);
    await new Promise((resolve) => window.setTimeout(resolve, 1000));
  } finally {
    setStaffControlsBusy(false);
    staffActionBusy = false;
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
 * Guarantees single-flight execution, disables the button, shows progress,
 * and adds a mandatory cooldown to prevent skipping tickets accidentally.
 */
async function callNext() {
  if (callingNext || staffActionBusy) return;
  callingNext = true;

  const callBtn = document.querySelector('#callNext');
  if (callBtn) {
    callBtn.dataset.originalHtml ??= callBtn.innerHTML;
    callBtn.disabled = true;
    callBtn.setAttribute('aria-busy', 'true');
    callBtn.innerHTML = `<span class="material-symbols-outlined spin">sync</span><span>Calling next...</span>`;
  }
  setStaffControlsBusy(true);

  let success = false;
  try {
    // Tinatawag muna ang next-ticket endpoint bago kunin ang bagong active ticket.
    const nextTicket = await request('/next', { method: 'POST' });
    success = true;
    const status = await request('/status');
    const currentCode = status.current?.displayCode || nextTicket?.displayCode;
    announceTicket(currentCode);
    await refreshStaff();
    showToast(`Next ticket called: ${currentCode}`);
  } catch (error) {
    if (error.status === 429) {
      showToast('Please wait a moment before calling another ticket.');
    } else {
      showToast('There are no waiting tickets to call.');
    }
    console.error(error);
  } finally {
    if (callBtn) {
      callBtn.setAttribute('aria-busy', 'false');
    }

    if (success) {
      // 3-second cooldown prevents accidental double-clicking or rapid ticket drainage
      await coolDownCallNextButton(3);
    } else {
      // Brief debounce delay on error or empty queue
      await new Promise((resolve) => window.setTimeout(resolve, 1200));
      if (callBtn && callBtn.dataset.originalHtml) {
        callBtn.innerHTML = callBtn.dataset.originalHtml;
      }
    }

    setStaffControlsBusy(false);
    callingNext = false;
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

// Attach event listeners
document.querySelectorAll('[data-ticket-type]').forEach((button) => {
  // Ang button value ang nagsasabi kung REGULAR o PRIORITY ang ipapasok sa queue.
  button.addEventListener('click', () => joinQueue(button.dataset.ticketType));
});

document.querySelector('#printTicket')?.addEventListener('click', () => window.print());

document.querySelector('#callNext')?.addEventListener('click', callNext);

document.querySelector('#recallTicket')?.addEventListener('click', async () => {
  if (callingNext || staffActionBusy) return;
  staffActionBusy = true;
  setStaffControlsBusy(true);

  try {
    const ticket = await request('/recall', { method: 'POST' });
    announceTicket(ticket.displayCode);
    showToast(`${ticket.displayCode} recalled.`);
    await new Promise((resolve) => window.setTimeout(resolve, 1500));
  } catch (error) {
    showToast('There is no active ticket to recall.');
  } finally {
    setStaffControlsBusy(false);
    staffActionBusy = false;
  }
});

document.querySelector('#skipTicket')?.addEventListener('click', () => staffAction('/skip', 'marked as no-show.'));
document.querySelector('#historySearch')?.addEventListener('input', refreshHistory);
document.querySelector('#secretTrigger')?.addEventListener('click', () => {
  document.querySelector('#adminPanel').classList.toggle('visible');
});

// Page-specific initializations
if (document.querySelector('#kioskNowServing')) {
  // Kiosk page: check persistent cooldown and poll now-serving ticket
  checkPersistentKioskCooldown();
  startRefreshLoop(refreshKiosk, 5000);
}

if (document.querySelector('#waitingCount')) {
  // Staff page: auto-refresh counters every 10 seconds
  startRefreshLoop(refreshStaff, 10000);
}

if (document.querySelector('#monitorCurrent')) {
  // Monitor page: update clock and queue display
  updateMonitorClock();
  window.setInterval(updateMonitorClock, 1000);
  startRefreshLoop(refreshMonitor, 5000);
}
