package dev.frost819.newbv.app.network

/**
 * Cookie 登录远程输入功能标签。
 *
 * @param onSubmit 提交处理（解析→nav 服务端校验→持久化登录），返回结果消息
 *   （成功为「登录成功…」、失败为具体原因），消息原样回传手机页面展示。
 */
class CookieLoginRemoteFeature
    private constructor(
        onSubmit: suspend (String) -> String,
    ) : RemoteConsoleFeature(
        id = ID,
        label = "Cookie 登录",
        iconPath =
            "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 " +
                "2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z",
        panelHtml = PANEL_HTML,
        actions =
            mapOf(
                "submit" to { body -> onSubmit(body) },
            ),
    ) {
        companion object {
            const val ID = "cookie_login"

            /**
             * @param onSubmit 提交处理（通常为 LoginViewModel.loginWithCookieRemote）。
             */
            fun create(onSubmit: suspend (String) -> String): CookieLoginRemoteFeature = CookieLoginRemoteFeature(onSubmit)

            private val PANEL_HTML =
                """
        <style>
          #cl_panel .cl-field { display: flex; flex-direction: column; gap: 12px; }
          #cl_input {
            width: 100%; min-height: 160px; padding: 14px 16px;
            border: 1px solid var(--md-outline); border-radius: 12px;
            background: transparent; color: var(--md-on-surface);
            font-size: 15px; line-height: 22px; font-family: inherit;
            outline: none; caret-color: var(--md-primary); resize: vertical;
          }
          #cl_input:focus { border-color: var(--md-primary); border-width: 2px; padding: 13px 15px; }
          #cl_input::placeholder { color: var(--md-on-surface-variant); }
          .cl-btn {
            height: 44px; padding: 0 24px;
            border: none; border-radius: 22px;
            background: var(--md-primary); color: var(--md-on-primary);
            font-size: 14px; font-weight: 600; font-family: inherit;
            cursor: pointer; width: 100%;
          }
          .cl-btn:disabled { opacity: 0.45; }
          .cl-btn.tonal { background: var(--md-secondary-container); color: var(--md-on-secondary-container); }
          #cl_file { display: none; }
          #cl_status { min-height: 20px; font-size: 14px; line-height: 20px; color: var(--md-on-surface-variant); white-space: pre-wrap; }
        </style>
        <div id="cl_panel" class="cl-field">
          <p style="margin:0;font-size:13px;line-height:20px;color:var(--md-on-surface-variant)">粘贴浏览器 Cookie（含 SESSDATA、bili_jct、DedeUserID）或 newBV / NeoBV 凭证 JSON。发送后将自动在电视上登录，结果会显示在手机页面。</p>
          <textarea id="cl_input" class="md-input" maxlength="65536" autocomplete="off" spellcheck="false" placeholder="在这里粘贴 Cookie 或凭证 JSON"></textarea>
          <input type="file" id="cl_file" accept=".json,.txt,application/json,text/plain" />
          <button id="cl_import" class="cl-btn tonal" onclick="cl_pickFile()">从文件导入凭证</button>
          <button id="cl_send" class="cl-btn" onclick="cl_submit()">发送并在电视上登录</button>
          <p id="cl_status" role="status" aria-live="polite"></p>
        </div>
        <script>
          (function () {
            const input = document.getElementById('cl_input');
            const send = document.getElementById('cl_send');
            const status = document.getElementById('cl_status');
            let fileContent = '';

            window.cl_status = function (text) { status.textContent = text; };
            window.cl_pickFile = function () { document.getElementById('cl_file').click(); };

            document.getElementById('cl_file').addEventListener('change', async event => {
              const file = event.target.files[0];
              if (!file) return;
              if (file.size > 65536) { window.mdSnackbar('文件过大，请选择小于 64 KB 的凭证文件'); event.target.value = ''; return; }
              try {
                fileContent = await file.text();
                input.value = fileContent;
                window.mdSnackbar('文件已导入，点击「发送并在电视上登录」');
              } catch { window.mdSnackbar('无法读取文件，请重新选择'); }
            });

            window.cl_submit = async function () {
              const value = input.value;
              if (!value.trim()) { window.mdSnackbar('请输入 Cookie 或凭证 JSON'); return; }
              send.disabled = true;
              window.mdSnackbar('正在发送并在电视上登录，请稍候…', true);
              try {
                const res = await window.remotePost('cookie_login', 'submit', value);
                if (res.ok) {
                  window.mdSnackbar('✅ ' + res.text);
                  status.textContent = res.text;
                  input.value = ''; fileContent = '';
                } else {
                  window.mdSnackbar('❌ ' + res.text);
                  status.textContent = res.text;
                }
              } catch {
                window.mdSnackbar('发送失败，请检查网络并确认电视仍在 Cookie 登录页');
              } finally {
                send.disabled = false;
              }
            };

            input.addEventListener('keydown', function (e) { if (e.key === 'Enter' && e.ctrlKey) cl_submit(); });
          })();
        </script>
            """
        }
    }
