package dev.frost819.newbv.app.network

/**
 * 搜索远程输入功能标签（参考 FongMi/TV：手机提交关键词，电视端立即搜索）。
 *
 * @param onSearch 关键词提交回调（调用方保证在主线程语义内执行电视端搜索导航）
 */
class SearchRemoteFeature private constructor(
    onSearch: suspend (String) -> Unit,
) : RemoteConsoleFeature(
        id = ID,
        label = "搜索",
        iconPath =
            "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 " +
                "5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 " +
                "5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
        panelHtml = PANEL_HTML,
        actions =
            mapOf(
                "submit" to { body ->
                    val keyword = body.trim()
                    if (keyword.isEmpty()) {
                        "请输入搜索关键词"
                    } else {
                        onSearch(keyword)
                        "已在电视上搜索：$keyword"
                    }
                },
            ),
    ) {
    companion object {
        const val ID = "search"

        fun create(onSearch: suspend (String) -> Unit): SearchRemoteFeature = SearchRemoteFeature(onSearch)

        /** 面板：关键词输入 + 提交（JS 用字符串拼接避免模板字面量） */
        private val PANEL_HTML =
            """
        <style>
          #sr_panel .sr-field { display: flex; flex-direction: column; gap: 12px; }
          #sr_keyword {
            width: 100%; padding: 14px 16px;
            border: 1px solid var(--md-outline); border-radius: 12px;
            background: transparent; color: var(--md-on-surface);
            font-size: 16px; font-family: inherit;
            outline: none; caret-color: var(--md-primary);
          }
          #sr_keyword:focus { border-color: var(--md-primary); border-width: 2px; padding: 13px 15px; }
          #sr_keyword::placeholder { color: var(--md-on-surface-variant); }
          .sr-btn {
            height: 44px; padding: 0 24px;
            border: none; border-radius: 22px;
            background: var(--md-primary); color: var(--md-on-primary);
            font-size: 14px; font-weight: 600; font-family: inherit; cursor: pointer;
          }
          .sr-btn:disabled { opacity: 0.45; }
          #sr_status { min-height: 20px; font-size: 14px; color: var(--md-on-surface-variant); }
        </style>
        <div id="sr_panel" class="sr-field">
          <p style="margin:0;font-size:13px;line-height:20px;color:var(--md-on-surface-variant)">在手机上输入关键词，提交后电视端会立即执行搜索。手机与电视需连接同一网络。</p>
          <div class="sr-field">
            <input id="sr_keyword" class="md-input" maxlength="100" autocomplete="off" placeholder="请输入搜索关键词" />
            <button id="sr_send" class="sr-btn" onclick="sr_submit()">在电视上搜索</button>
            <p id="sr_status" role="status" aria-live="polite"></p>
          </div>
        </div>
        <script>
          (function () {
            const keyword = document.getElementById('sr_keyword');
            const send = document.getElementById('sr_send');
            const status = document.getElementById('sr_status');

            window.sr_submit = async function () {
              const value = keyword.value;
              if (!value.trim()) { window.mdSnackbar('请输入搜索关键词'); return; }
              send.disabled = true;
              try {
                const res = await remotePost('search', 'submit', value);
                window.mdSnackbar(res.text);
                status.textContent = res.text;
                if (res.ok) keyword.value = '';
              } catch {
                window.mdSnackbar('发送失败，请检查网络并确认电视仍在搜索页');
              } finally {
                send.disabled = false;
              }
            };

            keyword.addEventListener('keydown', function (e) { if (e.key === 'Enter') sr_submit(); });
          })();
        </script>
            """
    }
}
