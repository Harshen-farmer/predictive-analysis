(function () {
    "use strict";

    const unsafeMethods = new Set(["POST", "PUT", "PATCH", "DELETE"]);
    let csrfPromise;
    let toastRegion;
    const routes = new Map();
    let dispatchRoute;

    async function csrf() {
        if (!csrfPromise) {
            csrfPromise = fetch("/api/csrf", {
                headers: { Accept: "application/json" },
                credentials: "same-origin"
            }).then(async (response) => {
                if (!response.ok) {
                    throw new Error("Unable to initialize request security.");
                }
                return response.json();
            }).catch((error) => {
                csrfPromise = undefined;
                throw error;
            });
        }
        return csrfPromise;
    }

    async function request(url, options) {
        const requestOptions = Object.assign({}, options || {});
        const method = (requestOptions.method || "GET").toUpperCase();
        const headers = new Headers(requestOptions.headers || {});
        headers.set("Accept", headers.get("Accept") || "application/json");

        if (unsafeMethods.has(method)) {
            const token = await csrf();
            headers.set(token.headerName, token.token);
        }

        requestOptions.method = method;
        requestOptions.headers = headers;
        requestOptions.credentials = "same-origin";
        return fetch(url, requestOptions);
    }

    async function json(url, options) {
        const response = await request(url, options);
        let body;
        try {
            body = await response.json();
        } catch (error) {
            if (response.ok) {
                throw new Error("The server returned an invalid response.");
            }
        }
        if (!response.ok) {
            const message = body && (body.message || body.error);
            const failure = new Error(message || "The request could not be completed.");
            failure.status = response.status;
            failure.body = body;
            throw failure;
        }
        return body;
    }

    function showToast(message, type, duration) {
        if (!toastRegion) {
            toastRegion = document.createElement("div");
            toastRegion.className = "toast-region";
            toastRegion.setAttribute("role", "region");
            toastRegion.setAttribute("aria-label", "Notifications");
            toastRegion.setAttribute("aria-live", "polite");
            document.body.appendChild(toastRegion);
        }

        const toast = document.createElement("div");
        toast.className = "toast" + (type === "error" ? " toast-error" : " toast-success");
        toast.setAttribute("role", type === "error" ? "alert" : "status");
        toast.textContent = message;
        toastRegion.appendChild(toast);
        window.setTimeout(() => toast.remove(), duration || 4000);
    }

    function openModal(title, content) {
        const dialog = document.createElement("dialog");
        dialog.className = "modal-dialog";
        const panel = document.createElement("section");
        panel.className = "modal";
        panel.setAttribute("aria-labelledby", "modal-title");
        const header = document.createElement("header");
        header.className = "modal-header";
        const heading = document.createElement("h2");
        heading.id = "modal-title";
        heading.textContent = title;
        const closeButton = document.createElement("button");
        closeButton.className = "btn btn-secondary";
        closeButton.type = "button";
        closeButton.textContent = "Close";
        closeButton.setAttribute("aria-label", "Close dialog");
        closeButton.addEventListener("click", () => dialog.close());
        header.append(heading, closeButton);
        panel.appendChild(header);

        const body = document.createElement("div");
        if (content instanceof Node) {
            body.appendChild(content);
        } else {
            body.textContent = String(content);
        }
        panel.appendChild(body);
        dialog.appendChild(panel);
        dialog.addEventListener("close", () => dialog.remove(), { once: true });
        document.body.appendChild(dialog);
        dialog.showModal();
        return dialog;
    }

    function confirmDialog(message, title) {
        return new Promise((resolve) => {
            const dialog = document.createElement("dialog");
            dialog.className = "confirm-dialog";
            const heading = document.createElement("h2");
            heading.textContent = title || "Please confirm";
            const text = document.createElement("p");
            text.textContent = message;
            const actions = document.createElement("div");
            actions.className = "confirm-actions";
            const cancel = document.createElement("button");
            cancel.className = "btn btn-secondary";
            cancel.type = "button";
            cancel.textContent = "Cancel";
            cancel.addEventListener("click", () => dialog.close("cancel"));
            const confirm = document.createElement("button");
            confirm.className = "btn btn-danger";
            confirm.type = "button";
            confirm.textContent = "Confirm";
            confirm.addEventListener("click", () => dialog.close("confirm"));
            actions.append(cancel, confirm);
            dialog.append(heading, text, actions);
            dialog.addEventListener("close", () => {
                dialog.remove();
                resolve(dialog.returnValue === "confirm");
            }, { once: true });
            document.body.appendChild(dialog);
            dialog.showModal();
            cancel.focus();
        });
    }

    async function currentUser() {
        const response = await request("/api/me");
        if (!response.ok) {
            return null;
        }
        return response.json();
    }

    async function requireRole(role) {
        const user = await currentUser();
        if (!user || user.role !== role) {
            const destination = user
                ? (user.role === "ADMIN" ? "/admin/dashboard" : "/staff/dashboard")
                : (role === "ADMIN" ? "/admin/login" : "/staff/login");
            window.location.assign(destination);
            return null;
        }
        return user;
    }

    function route(path, handler) {
        routes.set(path, handler);
    }

    function startRouter() {
        dispatchRoute = function () {
            const handler = routes.get(window.location.pathname);
            if (handler) {
                handler();
            }
        };
        window.addEventListener("popstate", dispatchRoute);
        dispatchRoute();
    }

    function navigate(path) {
        window.history.pushState({}, "", path);
        if (dispatchRoute) {
            dispatchRoute();
        }
    }

    async function logout(button) {
        button.disabled = true;
        try {
            const response = await request(button.dataset.logout, { method: "POST" });
            if (!response.ok) {
                throw new Error("Unable to log out. Please try again.");
            }
            window.location.assign(button.dataset.loginUrl);
        } catch (error) {
            button.disabled = false;
            showToast(error.message || "Unable to log out. Please try again.", "error");
        }
    }

    async function initializeShell() {
        const shell = document.querySelector("[data-portal-role]");
        if (!shell) {
            return;
        }
        const user = await requireRole(shell.dataset.portalRole);
        if (!user) {
            return;
        }
        document.querySelectorAll("[data-current-user]").forEach((element) => {
            element.textContent = user.username;
        });
        document.querySelectorAll("[data-current-date]").forEach((element) => {
            element.textContent = new Intl.DateTimeFormat(undefined, {
                dateStyle: "medium"
            }).format(new Date());
        });
        document.querySelectorAll("[data-logout]").forEach((button) => {
            button.addEventListener("click", () => logout(button));
        });
    }

    window.HealthcareApp = Object.freeze({
        request,
        json,
        currentUser,
        requireRole,
        showToast,
        openModal,
        confirmDialog,
        route,
        navigate,
        startRouter,
        initializeShell
    });

    document.addEventListener("DOMContentLoaded", initializeShell);
}());
