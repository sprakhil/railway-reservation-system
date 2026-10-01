/* Tokens survive refresh within a gateway run, but not logout or a gateway restart. */
(function () {
  "use strict";
  const instanceKey = "railway.swagger.instance";
  const initializer = window.onload;
  let activeInstance;

  function clearAuthorization() {
    window.ui?.authActions.logout(["bearerAuth"]);
    localStorage.removeItem("authorized");
  }

  async function checkInstance() {
    try {
      const response = await fetch("/swagger-session", { cache: "no-store", credentials: "same-origin" });
      if (!response.ok) throw new Error("Gateway unavailable");
      const { instanceId } = await response.json();
      if (typeof instanceId !== "string" || !instanceId) throw new Error("Missing gateway instance");
      const previous = activeInstance ?? localStorage.getItem(instanceKey);
      const changed = previous !== instanceId;
      if (changed) clearAuthorization();
      activeInstance = instanceId;
      localStorage.setItem(instanceKey, instanceId);
      return !changed;
    } catch {
      clearAuthorization();
      localStorage.removeItem(instanceKey);
      activeInstance = undefined;
      return false;
    }
  }

  function removeBearer(request) {
    for (const name of Object.keys(request.headers || {})) {
      if (name.toLowerCase() === "authorization") delete request.headers[name];
    }
  }

  window.onload = async function (...args) {
    await checkInstance();
    const Bundle = window.SwaggerUIBundle;
    window.SwaggerUIBundle = new Proxy(Bundle, {
      apply(target, receiver, parameters) {
        const options = parameters[0];
        const beforeRequest = options.requestInterceptor;
        const afterResponse = options.responseInterceptor;
        options.requestInterceptor = async function (request) {
          const result = beforeRequest ? await beforeRequest(request) : request;
          const url = new URL(result.url, window.location.origin);
          // Also handle a tab that stayed open while the gateway restarted.
          if (url.origin === window.location.origin && url.pathname.startsWith("/api/")) {
            if (!await checkInstance()) removeBearer(result);
          }
          return result;
        };
        options.responseInterceptor = async function (response) {
          const result = afterResponse ? await afterResponse(response) : response;
          const url = new URL(result.url, window.location.origin);
          if (url.origin === window.location.origin && result.status >= 200 && result.status < 300
              && /^\/api\/auth\/logout(?:-all)?\/?$/.test(url.pathname)) {
            clearAuthorization();
          }
          return result;
        };
        return Reflect.apply(target, receiver, parameters);
      }
    });
    return initializer.apply(this, args);
  };
}());
