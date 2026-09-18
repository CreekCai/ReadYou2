(function () {
    'use strict';
    const article = document.querySelector('article');
    let active = null, sequence = 0;
    const style = document.createElement('style');
    style.textContent = `
      .ry-note { margin: 1em 0; padding: 0 0 0 .8em; border-left: 3px solid var(--link-text-color); border-radius: 2px; font: inherit; line-height: inherit; color: var(--text-color); background: none; text-align: start; }
      .ry-note p { margin: .45em 0; white-space: pre-wrap; overflow-wrap: anywhere; }
      .ry-note .ry-label { color: var(--link-text-color); }
      .ry-note button, .ry-reopen { background: none; border: 0; color: var(--link-text-color); font: inherit; padding: .35em .6em .35em 0; min-height: 44px; cursor: pointer; }
      .ry-note textarea { display: block; box-sizing: border-box; width: 100%; min-height: 2.6em; max-height: 8em; border: 0; border-bottom: 1px solid var(--link-text-color); border-radius: 0; background: transparent; color: inherit; font: inherit; line-height: inherit; padding: .4em 0; resize: vertical; outline: none; }
      .ry-note textarea:focus-visible { border-bottom-width: 2px; }
      .ry-note a { overflow-wrap: anywhere; }
      .ry-note iframe { border: 0; width: 100%; min-height: 100px; }
      .ry-note details { margin-top: .6em; }
    `;
    document.head.append(style);
    const node = (tag, text, parent) => { const e = document.createElement(tag); if (text) e.textContent = text; if (parent) parent.append(e); return e; };
    function button(text, parent, action) { const b = node('button', text, parent); b.type = 'button'; b.onclick = action; return b; }
    function composer(state) {
        if (state.editor || state.busy) return;
        const box = node('div', '', state.block); state.editor = box; state.actions.hidden = true;
        const field = node('textarea', '', box); field.placeholder = '继续问这个问题…'; field.maxLength = 4000;
        field.setAttribute('aria-label', '输入追问，自动结合本文上下文');
        button('取消', box, () => { field.blur(); box.remove(); state.editor = null; state.actions.hidden = false; });
        button('发送', box, () => { const q = field.value.trim(); if (!q) return; field.blur(); box.remove(); state.editor = null; state.actions.hidden = false; ask(state, q); });
        field.focus(); field.scrollIntoView({block: 'nearest'});
    }
    function ask(state, question) {
        if (state.busy || state !== active) return;
        if (state.errorNode) { state.errorNode.remove(); state.errorNode = null; }
        if (state.retry) { state.retry.remove(); state.retry = null; }
        state.busy = true; state.lastQuestion = question;
        state.actions.replaceChildren();
        state.pending = node('p', '正在结合本文解释…', state.block);
        button('停止', state.actions, () => {
            ReadYouExplain.cancel(); state.busy = false; state.pending.remove(); actions(state);
        });
        ReadYouExplain.ask(state.id, JSON.stringify({selected: state.selected, paragraph: state.paragraph, question, history: state.history}));
    }
    function actions(state) {
        state.actions.replaceChildren();
        button('继续问', state.actions, () => composer(state));
        button('收起', state.actions, () => {
            ReadYouExplain.cancel(); state.busy = false;
            state.block.hidden = true;
            if (!state.reopen) {
                state.reopen = button('查看释义', null, () => { state.block.hidden = false; state.reopen.remove(); state.reopen = null; });
                state.reopen.className = 'ry-reopen'; state.anchor.after(state.reopen);
            }
        });
        state.block.append(state.actions);
    }
    window.ReadYouNotes = {
        select: function (mode) {
            const selection = window.getSelection();
            if (!selection || !selection.rangeCount || !selection.toString().trim()) return;
            const range = selection.getRangeAt(0);
            let element = range.endContainer.nodeType === 1 ? range.endContainer : range.endContainer.parentElement;
            if (!article.contains(element) || element.closest('.ry-note')) return;
            const selected = selection.toString().trim();
            if (selected.length > 12000) return;
            let anchor = element.closest('p,li,pre,blockquote,h1,h2,h3,h4,table') || element;
            while (anchor.parentElement && anchor.parentElement !== article && !['DIV','SECTION','ARTICLE'].includes(anchor.parentElement.tagName)) anchor = anchor.parentElement;
            const paragraph = anchor.textContent;
            ReadYouExplain.cancel();
            if (active) { active.block.remove(); if (active.reopen) active.reopen.remove(); }
            const block = node('aside'); block.className = 'ry-note'; block.setAttribute('aria-label', 'AI 本文释义');
            const label = node('p', '本文释义 · AI', block); label.className = 'ry-label';
            const state = {id: String(++sequence), selected, paragraph, anchor, block, history: [], busy: false, editor: null, actions: node('div')};
            active = state; anchor.after(block); selection.removeAllRanges(); actions(state);
            if (mode === 1) composer(state);
            else ask(state, mode === 2 ? '请结合本文语境，将选中内容翻译成简体中文。' : '选中内容在本文里是什么意思？请用两三句话解释。');
        },
        receive: function (id, result) {
            const state = active;
            if (!state || state.id !== id || !state.busy) return;
            state.busy = false; state.pending.remove();
            if (result.error) {
                state.errorNode = node('p', result.error, state.block); state.errorNode.setAttribute('role','alert');
                state.retry = button('重试', state.block, () => ask(state, state.lastQuestion));
            } else {
                if (state.history.length) node('p', '问：' + state.lastQuestion, state.block);
                node('p', result.text, state.block);
                if (result.searched) node('p', '已参考 Google 搜索', state.block).className = 'ry-label';
                if (result.sources && result.sources.length) {
                    const refs = node('details', '', state.block); node('summary', '查看来源', refs);
                    result.sources.forEach(source => { if (/^https?:\/\//.test(source.url)) { const p = node('p','',refs); const a = node('a', source.title, p); a.href = source.url; } });
                }
                if (result.searchHtml) {
                    const frame = node('iframe', '', state.block);
                    frame.title = 'Google 搜索建议';
                    frame.setAttribute('sandbox','allow-top-navigation-by-user-activation');
                    frame.srcdoc = '<base target="_top">' + result.searchHtml;
                }
                state.history.push({question: state.lastQuestion, answer: result.text});
            }
            actions(state);
        }
    };
})();
