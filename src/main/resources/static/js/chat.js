/**
 * chat.js — RAG Chat Demo
 *
 * Uses fetch() + ReadableStream to consume the SSE endpoint at /api/chat/stream.
 * This approach (vs. native EventSource) allows setting the Authorization header,
 * which EventSource does not support.
 */

(function () {
    'use strict';

    const questionEl  = document.getElementById('question');
    const tokenEl     = document.getElementById('token');
    const sendBtn     = document.getElementById('send-btn');
    const answerEl    = document.getElementById('answer');
    const citationsEl = document.getElementById('citations');
    const statusEl    = document.getElementById('status');

    // Accumulated raw SSE buffer across chunks (handles chunk boundaries mid-line)
    let sseBuffer = '';
    // AbortController for the active stream — cancelled on new request or page unload
    let activeController = null;

    /**
     * Parse lines from an SSE chunk and return an array of token strings.
     * SSE format per line: "data: <value>\n" — blank lines separate events.
     * We also handle the [DONE] sentinel that some implementations emit.
     */
    function parseSseTokens(chunk) {
        sseBuffer += chunk;
        // Normalise all SSE-legal line endings (\r\n, \r, \n) to \n before splitting
        const normalised = sseBuffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
        const lines = normalised.split('\n');
        // Keep the last potentially incomplete line back in the buffer
        sseBuffer = lines.pop();

        const tokens = [];
        for (const line of lines) {
            if (line.startsWith('data:')) {
                const value = line.slice(5).trim();
                if (value && value !== '[DONE]') {
                    tokens.push(value);
                }
            }
        }
        return tokens;
    }

    /**
     * Extract citation information from a completed answer text.
     * The backend may embed citations as a trailing JSON block or the caller
     * may supply them separately. Here we look for a conventional marker:
     *   \n\n[CITATIONS]\n<json array>
     * If not present, no citations are shown. This is extensible.
     */
    function extractCitations(fullText) {
        const marker = '\n\n[CITATIONS]\n';
        const idx = fullText.indexOf(marker);
        if (idx === -1) return { answer: fullText, citations: [] };

        const answerPart = fullText.slice(0, idx);
        try {
            const citations = JSON.parse(fullText.slice(idx + marker.length));
            return { answer: answerPart, citations: Array.isArray(citations) ? citations : [] };
        } catch {
            return { answer: answerPart, citations: [] };
        }
    }

    function renderCitations(citations) {
        citationsEl.innerHTML = '';
        if (!citations || citations.length === 0) {
            citationsEl.hidden = true;
            return;
        }

        const heading = document.createElement('h3');
        heading.textContent = 'Sources';
        citationsEl.appendChild(heading);

        const list = document.createElement('ul');
        list.className = 'citations__list';
        citations.forEach(function (c) {
            const li = document.createElement('li');
            li.className = 'citations__item';
            const name = c.source || c.docId || JSON.stringify(c);
            li.textContent = name;
            if (c.docId) {
                const small = document.createElement('small');
                small.className = 'citations__doc-id';
                small.textContent = ' (id: ' + c.docId + ')';
                li.appendChild(small);
            }
            list.appendChild(li);
        });
        citationsEl.appendChild(list);
        citationsEl.hidden = false;
    }

    function setStatus(msg, isError) {
        statusEl.textContent = msg;
        statusEl.className = 'chat__status' + (isError ? ' chat__status--error' : '');
        statusEl.hidden = !msg;
    }

    function setLoading(loading) {
        sendBtn.disabled = loading;
        sendBtn.setAttribute('aria-busy', loading ? 'true' : 'false');
        questionEl.disabled = loading;
    }

    async function startStream() {
        const question = questionEl.value.trim();
        if (!question) {
            questionEl.focus();
            return;
        }

        const token = tokenEl ? tokenEl.value.trim() : '';

        // Cancel any in-flight stream before starting a new one
        if (activeController) activeController.abort();
        activeController = new AbortController();

        // Reset UI
        answerEl.textContent = '';
        // Silence aria-live during streaming to avoid announcing every token
        answerEl.setAttribute('aria-live', 'off');
        citationsEl.replaceChildren();
        citationsEl.hidden = true;
        sseBuffer = '';
        setStatus('');
        setLoading(true);

        const url = '/api/chat/stream?question=' + encodeURIComponent(question);
        const headers = { 'Accept': 'text/event-stream' };
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }

        let fullAnswer = '';

        try {
            const response = await fetch(url, { headers: headers, signal: activeController.signal });

            if (!response.ok) {
                throw new Error('HTTP ' + response.status + ': ' + (response.statusText || 'Unknown error'));
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder('utf-8');

            // Stream loop — reads chunks until the server closes the stream
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                const chunk = decoder.decode(value, { stream: true });
                const tokens = parseSseTokens(chunk);

                tokens.forEach(function (t) {
                    fullAnswer += t;
                    answerEl.textContent += t;
                    // Auto-scroll the answer area
                    answerEl.scrollTop = answerEl.scrollHeight;
                });
            }

            // Flush any remaining buffer content (final partial line)
            if (sseBuffer.startsWith('data:')) {
                const trailing = sseBuffer.slice(5).trim();
                if (trailing && trailing !== '[DONE]') {
                    fullAnswer += trailing;
                    answerEl.textContent += trailing;
                }
            }

            // Post-stream: re-enable aria-live so assistive tech announces the final answer
            answerEl.setAttribute('aria-live', 'polite');

            // Extract and render citations if embedded in answer
            const { answer, citations } = extractCitations(fullAnswer);
            if (citations.length > 0) {
                answerEl.textContent = answer;
            }
            renderCitations(citations);
            setStatus('');

        } catch (err) {
            if (err.name !== 'AbortError') {
                console.error('[chat.js] Stream error:', err);
                setStatus('Error: ' + err.message, true);
            }
        } finally {
            answerEl.setAttribute('aria-live', 'polite');
            setLoading(false);
            activeController = null;
        }
    }

    // Cancel active stream on page unload so the server-side Flux is released immediately
    window.addEventListener('beforeunload', function () {
        if (activeController) activeController.abort();
    });

    // Wire up events once DOM is ready
    sendBtn.addEventListener('click', startStream);

    questionEl.addEventListener('keydown', function (e) {
        // Ctrl+Enter or Cmd+Enter submits the question
        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
            e.preventDefault();
            startStream();
        }
    });
}());
