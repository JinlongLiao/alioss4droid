const NATIVE_APP_ID = "aliyun_session_bridge";
const ALLOWED_BASE_DOMAINS = ["aliyun.com", "alibabacloud.com"];
const port = browser.runtime.connectNative(NATIVE_APP_ID);

function isAllowedDomain(domain) {
  const normalized = String(domain || "").replace(/^\./, "").toLowerCase();
  return ALLOWED_BASE_DOMAINS.some(
    base => normalized === base || normalized.endsWith(`.${base}`)
  );
}

async function exportCookies() {
  const groups = await Promise.all(
    ALLOWED_BASE_DOMAINS.map(domain => browser.cookies.getAll({domain}))
  );
  const cookies = groups.flat().filter(cookie => isAllowedDomain(cookie.domain));
  port.postMessage({type: "cookies", cookies});
}

async function restoreCookies(cookies) {
  for (const cookie of Array.isArray(cookies) ? cookies : []) {
    if (!isAllowedDomain(cookie.domain) || typeof cookie.name !== "string") continue;
    const host = cookie.domain.replace(/^\./, "");
    const path = typeof cookie.path === "string" && cookie.path.startsWith("/")
      ? cookie.path
      : "/";
    const details = {
      url: `https://${host}${path}`,
      name: cookie.name,
      value: typeof cookie.value === "string" ? cookie.value : "",
      domain: cookie.domain,
      path,
      secure: cookie.secure !== false,
      httpOnly: cookie.httpOnly === true,
      sameSite: cookie.sameSite
    };
    if (typeof cookie.expirationDate === "number") {
      details.expirationDate = cookie.expirationDate;
    }
    await browser.cookies.set(details).catch(() => undefined);
  }
  await exportCookies();
  port.postMessage({type: "restored"});
}

port.onMessage.addListener(message => {
  if (!message || typeof message.type !== "string") return;
  if (message.type === "export") exportCookies();
  if (message.type === "restore") restoreCookies(message.cookies);
  if (message.type === "clear") {
    Promise.all(ALLOWED_BASE_DOMAINS.map(domain =>
      browser.cookies.getAll({domain}).then(cookies => Promise.all(cookies.map(cookie => {
        const host = cookie.domain.replace(/^\./, "");
        return browser.cookies.remove({
          url: `https://${host}${cookie.path || "/"}`,
          name: cookie.name
        });
      })))
    )).then(() => port.postMessage({type: "cleared"}));
  }
});

port.postMessage({type: "ready"});
