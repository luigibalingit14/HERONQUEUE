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
 * Creates a branded, one-page PDF from the UMak ticket artwork already on the
 * kiosk page, then downloads it without requiring a print dialog.
 */
async function downloadTicketPdf(ticket, peopleAhead) {
  const seal = document.querySelector('.print-ticket-seal');
  const wordmark = document.querySelector('.print-ticket-wordmark');
  if (!seal || !wordmark) throw new Error('Ticket logo assets are unavailable.');
  await Promise.all([seal.decode(), wordmark.decode()]);

  const canvas = document.createElement('canvas');
  canvas.width = 1200;
  canvas.height = 1697;
  const context = canvas.getContext('2d');
  if (!context) throw new Error('Unable to render the ticket PDF.');

  context.fillStyle = '#ffffff';
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = '#f5ec3a';
  context.fillRect(0, 0, canvas.width, 16);

  const drawContained = (image, x, y, maxWidth, maxHeight) => {
    const scale = Math.min(maxWidth / image.naturalWidth, maxHeight / image.naturalHeight);
    const width = image.naturalWidth * scale;
    const height = image.naturalHeight * scale;
    context.drawImage(image, x + (maxWidth - width) / 2, y + (maxHeight - height) / 2, width, height);
  };

  drawContained(seal, 510, 64, 180, 180);
  drawContained(wordmark, 190, 272, 820, 130);
  context.fillStyle = '#105389';
  context.font = '700 28px Arial, sans-serif';
  context.textAlign = 'center';
  context.fillText('HERONQUEUE  |  STUDENT SERVICES', 600, 452);

  context.fillStyle = '#111c4e';
  context.font = '700 36px Arial, sans-serif';
  context.fillText('YOUR QUEUE TICKET', 600, 565);
  context.font = '700 144px Arial, sans-serif';
  context.fillText(ticket.displayCode, 600, 750);
  context.fillStyle = '#f5ec3a';
  context.fillRect(120, 805, 960, 8);

  const rows = [
    ['QUEUE LANE', `${ticket.ticketType} QUEUE`],
    ['DATE AND TIME ISSUED', new Date(ticket.createdAt).toLocaleString('en-PH', {
      dateStyle: 'long',
      timeStyle: 'short'
    })],
    ['PEOPLE AHEAD WHEN ISSUED', String(peopleAhead)],
    ['STATUS', 'WAITING']
  ];
  context.textAlign = 'left';
  rows.forEach(([label, value], index) => {
    const y = 930 + index * 132;
    context.fillStyle = '#5d6680';
    context.font = '600 27px Arial, sans-serif';
    context.fillText(label, 140, y);
    context.fillStyle = '#111c4e';
    context.font = '700 32px Arial, sans-serif';
    context.textAlign = 'right';
    context.fillText(value, 1060, y);
    context.textAlign = 'left';
    context.fillStyle = '#e8eaf0';
    context.fillRect(140, y + 34, 920, 2);
  });

  context.fillStyle = '#f5f6fa';
  context.fillRect(100, 1510, 1000, 110);
  context.fillStyle = '#424a60';
  context.font = '24px Arial, sans-serif';
  context.textAlign = 'center';
  context.fillText('Please keep this ticket number and watch the queue display.', 600, 1555);
  context.fillText('Have your ticket ready when your number is called.', 600, 1595);

  const jpeg = await new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob);
      else reject(new Error('Unable to create the ticket PDF image.'));
    }, 'image/jpeg', 0.94);
  });
  const imageBytes = new Uint8Array(await jpeg.arrayBuffer());
  const encoder = new TextEncoder();
  const chunks = [];
  let byteLength = 0;
  const append = (bytes) => {
    chunks.push(bytes);
    byteLength += bytes.length;
  };
  const appendText = (text) => append(encoder.encode(text));
  const offsets = [0];

  appendText('%PDF-1.4\n%\xE2\xE3\xCF\xD3\n');
  const addObject = (number, parts) => {
    offsets[number] = byteLength;
    appendText(`${number} 0 obj\n`);
    parts.forEach((part) => typeof part === 'string' ? appendText(part) : append(part));
    appendText('\nendobj\n');
  };
  addObject(1, ['<< /Type /Catalog /Pages 2 0 R >>']);
  addObject(2, ['<< /Type /Pages /Kids [3 0 R] /Count 1 >>']);
  addObject(3, [
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 419.53 595.28] ',
    '/Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>'
  ]);
  addObject(4, [
    `<< /Type /XObject /Subtype /Image /Width ${canvas.width} /Height ${canvas.height} `,
    `/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${imageBytes.length} >>\nstream\n`,
    imageBytes,
    '\nendstream'
  ]);
  const pageContent = encoder.encode('q 419.53 0 0 595.28 0 0 cm /Im0 Do Q');
  addObject(5, [
    `<< /Length ${pageContent.length} >>\nstream\n`,
    pageContent,
    '\nendstream'
  ]);

  const xrefOffset = byteLength;
  appendText('xref\n0 6\n0000000000 65535 f \n');
  for (let number = 1; number <= 5; number += 1) {
    appendText(`${String(offsets[number]).padStart(10, '0')} 00000 n \n`);
  }
  appendText(`trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF`);

  const pdf = new Blob(chunks, { type: 'application/pdf' });
  const url = URL.createObjectURL(pdf);
  const link = document.createElement('a');
  link.href = url;
  link.download = `HeronQueue-${ticket.displayCode}.pdf`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 60000);
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
    let peopleAhead = '-';
    try {
      const status = await request('/status');
      const ahead = status.waiting.findIndex((item) => item.id === ticket.id);
      peopleAhead = Math.max(ahead, 0);
      const aheadEl = document.querySelector('#peopleAhead');
      if (aheadEl) aheadEl.textContent = peopleAhead;
      if (receiptPeopleAhead) receiptPeopleAhead.textContent = peopleAhead;
    } catch (error) {
      console.error('Ticket created, but queue position could not be refreshed.', error);
    }

    try {
      await downloadTicketPdf(ticket, peopleAhead);
      showToast('Your ticket PDF was downloaded automatically.');
    } catch (error) {
      showToast('Ticket created, but the PDF could not be downloaded. Please print another copy.');
      console.error(error);
    }
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
